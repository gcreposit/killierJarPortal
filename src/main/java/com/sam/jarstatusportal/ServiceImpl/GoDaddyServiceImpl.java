package com.sam.jarstatusportal.ServiceImpl;


import com.sam.jarstatusportal.Entity.Domain;
import com.sam.jarstatusportal.Service.GoDaddyService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

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

}
