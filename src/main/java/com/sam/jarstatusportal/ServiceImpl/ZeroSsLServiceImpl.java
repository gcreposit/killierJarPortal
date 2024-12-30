package com.sam.jarstatusportal.ServiceImpl;

import com.sam.jarstatusportal.Service.ZeroSsLService;
import jakarta.transaction.Transactional;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ZeroSsLServiceImpl implements ZeroSsLService {


    @Value("${zerossl.api.key}")
    private String apiKey;

    @Value("${csr.upload.directory}")
    private String csrFilePath;

    @Value("${csrKey.upload.directory}")
    private String keyFilePath;

    @Value("${godaddy.api.key}")
    private String godaddyApiKey;

    @Value("${godaddy.api.secret}")
    private String godaddyApiSecret;

    @Autowired
    private RestTemplate restTemplate;

    private static final Logger logger = LoggerFactory.getLogger(ZeroSsLService.class);


    private void ensureDirectoryExists(Path directoryPath) {
        try {
            if (directoryPath != null && !Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
                logger.info("Directory created: {}", directoryPath);
            } else if (directoryPath != null) {
                logger.info("Directory already exists: {}", directoryPath);
            }
        } catch (IOException e) {
            logger.error("Failed to ensure directory exists: {}", directoryPath);
            throw new RuntimeException("Error ensuring directory existence: " + directoryPath, e);
        }
    }


    @Override
    public String createCertificateWithAutoCSR(String domain) {
        logger.info("Starting the SSL certificate creation process with CSR.");


// Ensure parent directories exist or create them
        ensureDirectoryExists(Paths.get(csrFilePath).getParent());
        ensureDirectoryExists(Paths.get(keyFilePath).getParent());

        try {
            logger.info("Generating CSR using OpenSSL...");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "C:\\Program Files\\OpenSSL-Win64\\bin\\openssl",
                    "req", "-nodes", "-newkey", "rsa:2048", "-sha256",
                    "-keyout", keyFilePath,
                    "-out", csrFilePath,
                    "-subj", "/CN=" + domain,
                    "-config", "C:\\Program Files\\OpenSSL-Win64\\bin\\cnf\\openssl.cnf"
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Capture output fo line readiugn
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.error("OpenSSL Output: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.error("Failed to generate CSR. Exit code: {}", exitCode);
                throw new RuntimeException("OpenSSL CSR generation failed.");
            }
            logger.info("CSR and private key generated successfully.");
        } catch (Exception e) {
            logger.error("Error generating CSR: {}", e.getMessage());
            throw new RuntimeException("Error generating CSR: ", e);
        }

        // Read the generated CSR file
        String csrContent;
        try {
            csrContent = new String(Files.readAllBytes(Paths.get(csrFilePath)));
        } catch (IOException e) {
            logger.error("Failed to read CSR file: {}", e.getMessage());
            throw new RuntimeException("Error reading CSR file: ", e);
        }

        // Step 2: Validate the CSR with ZeroSSL API Important step h
        logger.info("Validating the generated CSR...");
        String validateUrl = "https://api.zerossl.com/validation/csr?access_key=" + apiKey;
        Map<String, String> validateRequestBody = Map.of("csr", csrContent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> validateEntity = new HttpEntity<>(validateRequestBody, headers);

        try {
            ResponseEntity<String> validateResponse = restTemplate.exchange(validateUrl, HttpMethod.POST, validateEntity, String.class);
            if (validateResponse.getStatusCode() == HttpStatus.OK) {
                JSONObject jsonResponse = new JSONObject(validateResponse.getBody());
                boolean isValid = jsonResponse.getBoolean("valid");
                if (!isValid) {
                    logger.error("CSR validation failed. Error: {}", jsonResponse.optString("error"));
                    throw new RuntimeException("Invalid CSR: " + jsonResponse.optString("error"));
                }
                logger.info("CSR validated successfully.");
            } else {
                logger.error("Failed to validate CSR. HTTP Status: {}, Response: {}", validateResponse.getStatusCode(), validateResponse.getBody());
                throw new RuntimeException("Failed to validate CSR.");
            }
        } catch (Exception e) {
            logger.error("Error validating CSR: {}", e.getMessage());
            throw new RuntimeException("Error during CSR validation: ", e);
        }

        // Step 3: Create SSL certificate using the CSR hahah ❣️ most important step to validate your CSR
        logger.info("Creating SSL certificate with the validated CSR...");
        String createUrl = "https://api.zerossl.com/certificates?access_key=" + apiKey;

        Map<String, Object> createRequestBody = new HashMap<>();
        createRequestBody.put("certificate_domains", domain);
        createRequestBody.put("certificate_csr", csrContent);
        createRequestBody.put("certificate_validity_days", 90);
        createRequestBody.put("strict_domains", 1);

        HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(createRequestBody, headers);
        String certificateId;
        try {
            ResponseEntity<String> createResponse = restTemplate.exchange(createUrl, HttpMethod.POST, createEntity, String.class);
            if (createResponse.getStatusCode() == HttpStatus.OK) {
                logger.info("Certificate creation successful. Response: {}", createResponse.getBody());
                JSONObject jsonResponse = new JSONObject(createResponse.getBody());
                certificateId = jsonResponse.getString("id");
                logger.info("Certificate ID: {}", certificateId);

//                // Step 3: Trigger CNAME Verification this is major step which is breaked into two simple steps step-a step-b
                callDetailExtractionMethodForDnsRecord(certificateId, jsonResponse);
                return createResponse.getBody();

            } else {
                logger.error("Certificate creation failed. HTTP Status: {}, Response: {}", createResponse.getStatusCode(), createResponse.getBody());
                throw new RuntimeException("Failed to create certificate: " + createResponse.getBody());
            }
        } catch (Exception e) {
            logger.error("An error occurred during the certificate creation process: {}", e.getMessage());
            throw new RuntimeException("Error while creating SSL certificate: ", e);
        }
    }

    // -----------------------------   This method is used to verify the domain by Cname csr----------------------------------------------------------------
    public void callDetailExtractionMethodForDnsRecord(String certificateId, JSONObject jsonResponse) {
        logger.info("Starting CNAME verification for Certificate ID: {}", certificateId);

        // Extract CNAME details from the response
        if (jsonResponse.has("validation") && jsonResponse.getJSONObject("validation").has("other_methods")) {
            JSONObject otherMethods = jsonResponse.getJSONObject("validation").getJSONObject("other_methods");

            for (String domain : otherMethods.keySet()) {
                JSONObject domainDetails = otherMethods.getJSONObject(domain);
                String targetHost = domainDetails.getString("cname_validation_p1");
                String targetRecord = domainDetails.getString("cname_validation_p2");

                logger.info("Domain: {}", domain);
                logger.info("CNAME Target Host: {}", targetHost);
                logger.info("CNAME Target Record: {}", targetRecord);

                // Remove the ".gccloudinfo.com" suffix from targetHost
                if (targetHost.endsWith(".gccloudinfo.com")) {
                    targetHost = targetHost.substring(0, targetHost.indexOf(".gccloudinfo.com"));
                    logger.info("CNAME Target Host: {}", targetHost);
                }

                // Step 1: Add the CNAME record to GoDaddy using API
                addDnsRecordToGoDaddy(domain, "CNAME", targetHost, targetRecord);
            }


        } else {
            logger.error("CNAME validation details not found in the response.");
            throw new RuntimeException("CNAME validation details missing from API response.");
        }
    }

    // -----------------------------   This method is used to Add DNS Record -----------------------------------------------------------
    private void addDnsRecordToGoDaddy(String domain, String type, String name, String data) {
        logger.info("Adding DNS record to GoDaddy for domain: {}", domain);

        String url = "https://api.godaddy.com/v1/domains/" + domain + "/records/" + type + "/" + name;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "sso-key " + godaddyApiKey + ":" + godaddyApiSecret);

        // DNS Record Payload (wrapped in an array)
        Map<String, Object> dnsRecord = new HashMap<>();
        dnsRecord.put("data", data);
        dnsRecord.put("ttl", 3600); // Set TTL to 10 seconds

        // Wrap the record in an array
        List<Map<String, Object>> dnsRecords = new ArrayList<>();
        dnsRecords.add(dnsRecord);

        HttpEntity<List<Map<String, Object>>> entity = new HttpEntity<>(dnsRecords, headers);

        try {
            // Send PUT request to GoDaddy API
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.NO_CONTENT) {
                logger.info("DNS record added successfully to GoDaddy.");
            } else {
                logger.error("Failed to add DNS record. HTTP Status: {}", response.getStatusCode());
                logger.error("Response: {}", response.getBody());
                throw new RuntimeException("Failed to add DNS record: " + response.getBody());
            }

            // Wait for DNS propagation (2 minutes)  I will use Thread in Coming time
            logger.info("Waiting for DNS propagation for 2 minutes...");


        } catch (Exception e) {
            logger.error("An error occurred while adding DNS record to GoDaddy: {}", e.getMessage());
            throw new RuntimeException("Error adding DNS record to GoDaddy", e);
        }
    }


    // -----------------------------   This method is to get the certificate Id of Respective Domain--------------------------------
    public String getCertificateIdByDomain(String domain) {
        logger.info("Fetching certificate ID for domain: {}", domain);

        String url = "https://api.zerossl.com/certificates?access_key=" + apiKey;

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                if (jsonResponse.has("results")) {
                    for (Object certObject : jsonResponse.getJSONArray("results")) {
                        JSONObject cert = (JSONObject) certObject;
                        if (cert.getString("common_name").equals(domain) && cert.getString("status").equals("draft")) {
                            String certificateId = cert.getString("id");
                            logger.info("Found certificate ID: {}", certificateId);
                            return certificateId;
                        }
                    }
                }
            } else {
                logger.error("Failed to fetch certificates. HTTP Status: {}", response.getStatusCode());
                logger.error("Response: {}", response.getBody());
            }
        } catch (Exception e) {
            logger.error("An error occurred while fetching certificates: {}", e.getMessage());
            throw new RuntimeException("Error fetching certificate ID for domain: " + domain, e);
        }

        throw
                new RuntimeException("Certificate ID not found for domain: " + domain);
    }

    // -----------------------------   This method is to get Verify The last Step that is verification--------------------------------
    @Override
    public String verifyDomainByZeroSsl(String domain) {
        logger.info("Starting CNAME verification for domain: {}", domain);

        // Step 1: Fetch Certificate ID
        String certificateId = getCertificateIdByDomain(domain);

        // Step 2: Trigger CNAME Verification
        String url = "https://api.zerossl.com/certificates/" + certificateId + "/challenges?access_key=" + apiKey;

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("validation_method", "CNAME_CSR_HASH");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("Authorization", "Bearer " + apiKey); // Add Authorization header

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
        logger.info("Sending request to ZeroSSL: URL = {}, Headers = {}, Body = {}", url, headers, requestBody);
        logger.info("Entity ", entity);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("CNAME verification initiated successfully.");
                logger.info("Response: {}", response.getBody());
                return "Successfully verified";
            } else {
                logger.error("Failed to initiate CNAME verification. HTTP Status: {}", response.getStatusCode());
                logger.error("Response: {}", response.getBody());
                throw new RuntimeException("Failed to initiate CNAME verification. HTTP Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("An error occurred while initiating CNAME verification: {}", e.getMessage());
            throw new RuntimeException("Error initiating CNAME verification", e);
        }
    }




}
