package com.sam.jarstatusportal.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class GcsStorageConfig {

    private static final Logger logger = LoggerFactory.getLogger(GcsStorageConfig.class);

    @Bean
    public Storage storage() throws IOException {
        InputStream jsonStream;
        String externalFilePath = "/app/resources/jalshakti-1734933826605-883dbf306dbe.json"; // Your JSON Key Path
        String projectId = "jalshakti-1734933826605"; // Your Project ID

        // Load the service account JSON file
        java.io.File externalFile = new java.io.File(externalFilePath);
        if (externalFile.exists()) {
            // Load the file from the external location in Docker
            jsonStream = new FileInputStream(externalFile);
            logger.info("Loaded service account JSON file from Docker path: " + externalFilePath);
        } else {
            // Fallback to loading from classpath (Local environment)
            jsonStream = getClass()
                    .getClassLoader()
                    .getResourceAsStream("jalshakti-1734933826605-883dbf306dbe.json");
            if (jsonStream == null) {
                throw new IOException("Service account JSON file not found in classpath or Docker path.");
            }
            logger.info("Loaded service account JSON file from classpath.");
        }

        // Create GoogleCredentials and Storage instance
        GoogleCredentials credentials = GoogleCredentials.fromStream(jsonStream)
                .createScoped("https://www.googleapis.com/auth/cloud-platform");

        Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
                .getService();

        logger.info("Google Cloud Storage client initialized successfully for project: " + projectId);

        return storage;
    }
}

