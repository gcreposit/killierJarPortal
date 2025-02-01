package com.sam.jarstatusportal.ServiceImpl;


import com.sam.jarstatusportal.Entity.Domain;
import com.sam.jarstatusportal.Service.GoDaddyService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class GoDaddyServiceImpl implements GoDaddyService {


    @Value("${godaddy.api.key}")
    private String apiKey;

    @Value("${godaddy.api.secret}")
    private String apiSecret;

    @Autowired
    private RestTemplate restTemplate;


    //    TO list all the domains
    @Override
    public List<Domain> getDomains() {
        String url = "https://api.godaddy.com/v1/domains";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "sso-key " + apiKey + ":" + apiSecret);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Domain[]> response = restTemplate.exchange(url, HttpMethod.GET, entity, Domain[].class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return Arrays.asList(response.getBody());
        } else {
            throw new RuntimeException("Failed to fetch domains: " + response.getStatusCode());
        }
    }




    // Fetch domain details from GoDaddy API
    private Domain getDomainDetails(String domainName) {
        String url = "https://api.godaddy.com/v1/domains/" + domainName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "sso-key " + apiKey + ":" + apiSecret);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Domain> response = restTemplate.exchange(url, HttpMethod.GET, entity, Domain.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("Failed to fetch domain details: " + response.getStatusCode());
        }
    }

    // Fetch a specific domain along with its A record subdomains (ye h mast ek A function)
    @Override
    public Domain getDomainWithSubdomains(String domainName) {
        // Fetch domain details
        Domain domain = getDomainDetails(domainName);

        // Fetch A record subdomains
        List<String> subdomains = getSubdomains(domainName);
        domain.setSubdomains(subdomains);
        return domain;
    }


    // Fetch A record subdomains for a given domain so that I got
    private List<String> getSubdomains(String domain) {
        String url = "https://api.godaddy.com/v1/domains/" + domain + "/records/A";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "sso-key " + apiKey + ":" + apiSecret);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<List<Map<String, String>>> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, String>>>() {}
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Failed to fetch A records for " + domain + ": " + response.getStatusCode());
        }

        List<Map<String, String>> records = response.getBody();
        List<String> subdomainNames = new ArrayList<>();

        if (records != null) {
            for (Map<String, String> record : records) {
                String subdomain = record.get("name");
                if (!"@".equals(subdomain)) {
                    subdomainNames.add(subdomain + "." + domain);
                } else {
                    subdomainNames.add(domain);
                }
            }
        }

        return subdomainNames;
    }


}
