package com.sam.jarstatusportal.ServiceImpl;

import com.jcraft.jsch.*;
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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @Value("${vm.ip}")
    private String vmIp;

    @Value("${vm.userName}")
    private String vmUserName;

    @Value("${vm.password}")
    private String vmPassword;

    @Value("${upload.directory}")
    private String uploadDir;

    @Value("${vm.remoteFilePath}")
    private String remoteFilePath;


    @Autowired
    private RestTemplate restTemplate;

    private static final Logger logger = LoggerFactory.getLogger(ZeroSsLService.class);

    //-------------------------------- This can be Optimize jsut for the trial I made ,as it will be opimized after production -------------------------------
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

    // -----------------------------   This method is used to Create Certificate With Auto CSR ----------------------------------------------------------------
    @Override
    public String createCertificateWithAutoCSR(String domain) {
        logger.info("Starting the SSL certificate creation process with CSR.");


// Ensure parent directories exist or create them
        ensureDirectoryExists(Paths.get(csrFilePath).getParent());
        ensureDirectoryExists(Paths.get(keyFilePath).getParent());

        try {
            logger.info("Generating CSR using OpenSSL...");
            ProcessBuilder processBuilder = new ProcessBuilder("C:\\Program Files\\OpenSSL-Win64\\bin\\openssl", "req", "-nodes", "-newkey", "rsa:2048", "-sha256", "-keyout", keyFilePath, "-out", csrFilePath, "-subj", "/CN=" + domain, "-config", "C:\\Program Files\\OpenSSL-Win64\\bin\\cnf\\openssl.cnf");
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

    // -----------------------------   This method is used to Add DNS Record  In Go Daddy Domain-----------------------------------------------------------
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
    public String getCertificateIdByDomain(String domain, String status) {
        logger.info("Fetching certificate ID for domain: {}", domain);

        String url = "https://api.zerossl.com/certificates?access_key=" + apiKey;

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                if (jsonResponse.has("results")) {
                    for (Object certObject : jsonResponse.getJSONArray("results")) {
                        JSONObject cert = (JSONObject) certObject;
                        if (cert.getString("common_name").equals(domain) && cert.getString("status").equals(status)) {
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

        throw new RuntimeException("Certificate ID not found for domain: " + domain);
    }

    // -----------------------------   This method is to get Verify The last Step that is verification--------------------------------
    @Override
    public String verifyDomainByZeroSsl(String domain) {
        logger.info("Starting CNAME verification for domain: {}", domain);

        // Step 1: Fetch Certificate ID
        String certificateId = getCertificateIdByDomain(domain, "draft");

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

    // -----------------------------   This method is to Download the Certificate Using Zero SSL API-------------------------------
    @Override
    public String downlaodZeroSslCertificate(String domain) throws IOException {
        // Step 1: Fetch Certificate ID
//        String certificateId = "bb81d6d5fcc2f70a7d3b8eb936cb5006";
        String certificateId = getCertificateIdByDomain(domain, "issued");

        // Step 2: Build the API URL
        String urlString = "https://api.zerossl.com/certificates/" + certificateId + "/download?access_key=" + apiKey;

        // Step 3: Define the local file path
        String localFilePath = uploadDir + domain + "_certificate.zip";

        // Step 4: Download the certificate by leveraging the api of Zero SSL
        downloadZipFile(urlString, localFilePath);

        // Step Imp: Extract ZIP file to temporary directory
        Path tempDir = extractZipToTemp(localFilePath);
        logger.info("Unzip of File Succesfully", tempDir);


        // -----------------------------   This method is to Transfer Zip File to VM -------------------------------
        try {
            transferFileToVM(localFilePath, remoteFilePath, vmUserName, vmIp, vmPassword);
        } catch (Exception e) {
            logger.info("File transfer failed", e, Level.SEVERE);
        }

        // -----------------------------   This method is to Transfer Extracted Zip File to VM (It involves several steps first making it cpy to Temp File and then Transfer to VM-------------------------------
        try {
            transferExtractedFilesToVM(tempDir, remoteFilePath, vmUserName, vmIp, vmPassword, domain, keyFilePath);
        } catch (Exception e) {
            logger.info("Unzip of File  tranfer to VM failed", e, Level.SEVERE);
        }


        try {
            String port = "9999";
            // Step 1: Create configuration content
            String configContent = generateConfigContent(domain, port);

            // Step 2: Save configuration to a local file
            String localFilePathForConfFile = saveConfigToFile(domain, configContent, uploadDir);
            logger.info("Local Path For CONF File" + localFilePathForConfFile);


            transferConfFileToVM(localFilePathForConfFile, remoteFilePath, vmUserName, vmIp, vmPassword);
            logger.info("CONF Successfully TrFile" + localFilePathForConfFile);

        } catch (Exception e) {
            e.printStackTrace();
        }


        System.out.println("Certificate downloaded to: " + localFilePath);
        return localFilePath;


    }

    // Extract ZIP file into a temporary folder
    public static Path extractZipToTemp(String zipFilePath) throws IOException {
        Path tempDir = Files.createTempDirectory("extracted-zip");
        System.out.println("Temporary directory created at: " + tempDir.toAbsolutePath());

        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                Path filePath = tempDir.resolve(entry.getName());
                if (!entry.isDirectory()) {
                    extractFile(zipIn, filePath);
                } else {
                    Files.createDirectories(filePath);
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
        System.out.println("ZIP file extracted to: " + tempDir.toAbsolutePath());
        return tempDir;
    }

    // --------------------------------- Method to Extarct the zip content  -----------------------------
    private static void extractFile(ZipInputStream zipIn, Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath.toFile()))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    // --------------------------------- Method to download zip file to local system -----------------------------
    private void downloadZipFile(String urlString, String outputFilePath) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Ensure successful response
        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (InputStream inputStream = connection.getInputStream(); FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
            }
        } else {
            throw new IOException("Failed to download file. HTTP response code: " + connection.getResponseCode());
        }
    }

    // --------------------------------- Method For Transfer Zip File To VM -------------------------------------
    public static void transferFileToVM(String localFilePath, String remoteFilePath, String username, String host, String password) throws JSchException, SftpException, IOException {

        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channelExec = null;

        try {
            // Step 1: Establish SSH connection
            session = jsch.getSession(username, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            logger.info("Connecting to the VM...");
            session.connect();
            logger.info("Connection established!");

            // Prepare SCP command
            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                throw new IOException("Local file not found: " + localFilePath);
            }


            String command = "scp -t " + remoteFilePath;

            // Step 2: Open Exec channel and set command
            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            OutputStream out = channelExec.getOutputStream();
            FileInputStream fis = null;

            channelExec.connect();
            System.out.println("SCP channel opened.");

            // Step 3: Send file metadata  This is the Important Step  ❣️
            long fileSize = localFile.length();
            String commandHeader = "C0644 " + fileSize + " " + localFile.getName() + "\n";
            out.write(commandHeader.getBytes());
            out.flush();

            // Step 4: Send file content   This is the Important Step  ❣️
            fis = new FileInputStream(localFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            fis.close();

            // Signal end of file transfer
            out.write(0);
            out.flush();
            System.out.println("File transfer completed.");

        } catch (Exception e) {
            System.err.println("Error during SCP file transfer: " + e.getMessage());
            throw e;
        } finally {
            // Clean up resources
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
                System.out.println("SCP channel closed.");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                System.out.println("SSH session disconnected.");
            }
        }
    }

    // -----------------------------    This method is to Transfer Extraction of Zip File to VM (Note I used Recursively sending the file but before creation of Remote folder using SCP so it solved my Issue) -------------------------------
    public static void transferExtractedFilesToVM(Path localDir, String remoteDir, String username, String host, String password, String domain, String keyFilePath) throws JSchException, IOException, SftpException {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;

        try {
            // Step 1: Establish SSH connection
            session = jsch.getSession(username, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            logger.info("Connecting to the VM...");
            session.connect();
            logger.info("Connection established!");

            // Step 2: Open SFTP channel
            Channel channel = session.openChannel("sftp");
            channel.connect();
            sftpChannel = (ChannelSftp) channel;


            //--------Here I Creating Standarized FolderName so that issue wont come on running it  ------------
            String editedDomainName = domain.replace(".com", "");
            String folderName = "ssl_" + editedDomainName; // Use the name of the local directory
            String remoteFolder = remoteDir + "/" + folderName;
            try {
                sftpChannel.mkdir(remoteFolder); // Create the folder on the remote VM
                logger.info("Created remote folder: " + remoteFolder);
            } catch (SftpException e) {
                logger.info("Remote folder already exists: " + remoteFolder);
            }


            //--------Here I am sending the private key to the location on VM ------------
            File privateKeyFile = new File(keyFilePath);
            if (privateKeyFile.exists()) {
                sftpChannel.put(privateKeyFile.getAbsolutePath(), remoteFolder + "/private.key");
                logger.info("Transferred private.key to: " + remoteFolder);
            } else {
                logger.info("private.key file not found at: " + keyFilePath);
            }


            logger.info("Starting recursive transfer of extracted files...");
            uploadDirectory(sftpChannel, localDir.toFile(), remoteFolder);

            logger.info("All extracted files transferred successfully to: " + remoteDir);

        } catch (Exception e) {
            logger.info("Error during extracted files transfer: " + e.getMessage(), e, Level.SEVERE);
            throw e;
        } finally {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.disconnect();
                logger.info("SFTP channel closed.");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                logger.info("SSH session disconnected.");
            }
        }

    }

    // -----------------------------    This method is to transfer of file  to VM  which is called recursively in above method (Although it can be optimized)-------------------------------
    private static void uploadDirectory(ChannelSftp sftpChannel, File localDir, String remoteDir) throws SftpException {
        if (localDir.isDirectory()) {
            try {
                sftpChannel.mkdir(remoteDir); // Create directory on remote VM if it doesn't exist
            } catch (SftpException e) {
                // Directory might already exist, ignore
            }

            for (File file : localDir.listFiles()) {
                if (file.isFile()) {
                    // Transfer files
                    sftpChannel.put(file.getAbsolutePath(), remoteDir + "/" + file.getName());
                    logger.info("Transferred file: " + file.getAbsolutePath() + " to " + remoteDir);
                } else if (file.isDirectory()) {
                    // Recursively transfer directories
                    uploadDirectory(sftpChannel, file, remoteDir + "/" + file.getName());
                }
            }
        } else {
            throw new IllegalArgumentException("Provided local directory path is not a directory: " + localDir);
        }
    }

    // --------------------------------- Method to Generate the Config Content (--------Here Port is set statistically-------)-----------------------------
    private static String generateConfigContent(String domainName, String port) {
        String newDomainName = domainName.replace(".com", "");
        String sslDomain = "ssl_" + newDomainName;

        return """
                server {
                    listen 80;
                    server_name %s;
                    return 301 https://$host$request_uri;
                }
                
                # Configuration for %s
                server {
                    listen 443 ssl;
                    server_name %s;
                
                    ssl_certificate %s/certificate.crt;
                    ssl_certificate_key %s/private.key;
                    ssl_trusted_certificate %s/ca_bundle.crt;
                
                    client_max_body_size 20M;
                
                    location / {
                        proxy_pass http://127.0.0.1:%s;
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;
                    }
                }
                """.formatted(domainName, domainName, domainName, sslDomain, sslDomain, sslDomain, port);
    }

    // --------------------------------- Method to Save the Config File so that it can be leveraged-----------------------------
    private static String saveConfigToFile(String domain, String content, String uploadDir) throws IOException {
        String fileName = domain + ".conf";
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        // Construct the file path
        Path filePath = uploadPath.resolve(fileName);

        // Write content to the file
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Return the absolute path of the saved file as a String
        return filePath.toAbsolutePath().toString();
    }

    // --------------------------------- Method to Transfer the CONF file to VM-----------------------------
    public static void transferConfFileToVM(String localFilePath, String remoteFilePath, String username, String host, String password) throws JSchException, SftpException, IOException {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channelExec = null;

        try {
            // Step 1: Establish SSH connection
            session = jsch.getSession(username, host, 22);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            System.out.println("Connecting to the VM...");
            session.connect();
            System.out.println("Connection established!");

            // Prepare SCP command
            File localFile = new File(localFilePath);
            if (!localFile.exists()) {
                throw new IOException("Local file not found: " + localFilePath);
            }

            String command = "scp -t " + remoteFilePath;

            // Step 2: Open Exec channel and set command
            channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand(command);

            OutputStream out = channelExec.getOutputStream();
            FileInputStream fis = null;

            channelExec.connect();
            System.out.println("SCP channel opened.");

            // Step 3: Send file metadata
            long fileSize = localFile.length();
            String commandHeader = "C0644 " + fileSize + " " + localFile.getName() + "\n";
            out.write(commandHeader.getBytes());
            out.flush();

            // Step 4: Send file content
            fis = new FileInputStream(localFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            fis.close();

            // Signal end of file transfer
            out.write(0);
            out.flush();
            System.out.println("File transfer completed.");

        } catch (Exception e) {
            System.err.println("Error during SCP file transfer: " + e.getMessage());
            throw e;
        } finally {
            // Clean up resources
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
                System.out.println("SCP channel closed.");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                System.out.println("SSH session disconnected.");
            }
        }
    }


}
