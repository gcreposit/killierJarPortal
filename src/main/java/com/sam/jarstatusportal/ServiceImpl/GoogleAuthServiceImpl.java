package com.sam.jarstatusportal.ServiceImpl;

import com.google.auth.oauth2.GoogleCredentials;
import com.sam.jarstatusportal.Configuration.GcsStorageConfig;
import com.sam.jarstatusportal.Service.GoogleAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Service
@Transactional
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private static final String SCOPES = "https://www.googleapis.com/auth/cloud-platform";
    private static final String externalFilePath = "/app/resources/jalshakti-1734933826605-883dbf306dbe.json";
    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthServiceImpl.class);


    public String getAccessToken() throws IOException {
        InputStream jsonStream;

        java.io.File externalFile = new java.io.File(externalFilePath);
        if (externalFile.exists()) {
            // Load the file from the external location in Docker
            jsonStream = new FileInputStream(externalFile);
            logger.info("Loaded service account JSON file from Docker path: " + externalFilePath);
        }
        else {
            // Fallback to loading from classpath (Local environment)
            jsonStream = getClass()
                    .getClassLoader()
                    .getResourceAsStream("jalshakti-1734933826605-883dbf306dbe.json");
            if (jsonStream == null) {
                throw new IOException("Service account JSON file not found in classpath or Docker path.");
            }
            logger.info("Loaded service account JSON file from classpath.");
        }
        // Load service account credentials
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(jsonStream)
                .createScoped(SCOPES);

        // Refresh and get access token
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

}
