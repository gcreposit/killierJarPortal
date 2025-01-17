package com.sam.jarstatusportal.ServiceImpl;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.sam.jarstatusportal.Service.GdriveService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class GdriveServiceImpl implements GdriveService {


    private String googleDriveParentFolderId = "1TghigQdBizCe30Nbj9GffmcY8wiwXfll";

//    private static final String SERVICE_ACCOUNT_FILE = "src/main/resources/jalshakti-1734933826605-883dbf306dbe.json";
    private static final String APPLICATION_NAME = "jalshaktiSqlDisasterManagement";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final Drive driveService;

    public GdriveServiceImpl() throws IOException, GeneralSecurityException {
        this.driveService = initializeDriveService();
    }

    //    For Initializing the Drive
    private Drive initializeDriveService() throws IOException, GeneralSecurityException {

        InputStream jsonStream;
        String externalFilePath = "/app/resources/jalshakti-1734933826605-883dbf306dbe.json";

        java.io.File  externalFile = new java.io.File(externalFilePath);
        if (externalFile.exists()) {
            // Load the file from the external location in Docker
            jsonStream = new FileInputStream(externalFile);
        }
        else {
            // Load the file from the classpath (Local environment)
            String jsonFilePath = getClass()
                    .getClassLoader()
                    .getResource("jalshakti-1734933826605-883dbf306dbe.json")
                    .getPath();
            jsonStream = new FileInputStream(jsonFilePath);
        }

        GoogleCredential credential = GoogleCredential.fromStream(jsonStream)
                .createScoped(List.of(DriveScopes.DRIVE_FILE));

        return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    //    Just A DSA Implementation by SamCr7
    @Override
    public String findLatestFile(String projectName, String fileType) throws IOException {
        // Step 1: List subfolders under the parent folder
        FileList result = driveService.files().list()
                .setQ(String.format("'%s' in parents and mimeType='application/vnd.google-apps.folder'", googleDriveParentFolderId))
                .setFields("files(id, name)")
                .execute();

        List<File> folders = result.getFiles();
        if (folders == null || folders.isEmpty()) {
            throw new IOException("No subfolders found under the parent folder.");
        }

        // Step 2: Match folder name with the project
        String matchedFolderId = folders.stream()
                .filter(folder -> folder.getName().startsWith(projectName + "_"))
                .map(File::getId)
                .findFirst()
                .orElseThrow(() -> new IOException("Folder not found for project: " + projectName));

        // Step 3: Find the latest file (.zip or .sql) in the matched folder
        FileList fileList = driveService.files().list()
                .setQ(String.format("'%s' in parents and mimeType='%s'", matchedFolderId, fileType.trim().equalsIgnoreCase("zip") ? "application/zip" : "application/sql"))
                .setFields("files(id, name, createdTime)")
                .execute();

        List<File> files = fileList.getFiles();
        if (files == null || files.isEmpty()) {
            throw new IOException("No files of type " + fileType + " found in folder: " + matchedFolderId);
        }

        // Sort files by createdTime and return the latest
        return files.stream()
                .max(Comparator.comparing(file -> file.getCreatedTime().getValue()))
                .orElseThrow(() -> new IOException("Unable to find the latest file."))
                .getId();
    }

    @Override
    public java.io.File downloadFile(String fileId) throws IOException {
        // Fetch the file metadata to get its name
        File fileMetadata = driveService.files().get(fileId).setFields("name").execute();
        String fileName = fileMetadata.getName();


// Check and append the correct extension if missing
        if (!fileName.endsWith(".zip") && !fileName.endsWith(".sql")) {
            // Determine the correct extension based on content type or context
            String fileType = fileMetadata.getMimeType(); // Example: Use MIME type if available
            if (fileType.equals("application/zip")) {
                fileName += ".zip";
            } else if (fileType.equals("application/sql")) {
                fileName += ".sql";
            } else {
                throw new IOException("Unknown file type: " + fileType);
            }
        }
        // Temporary download location
        java.io.File outputFile = new java.io.File(System.getProperty("java.io.tmpdir") + "/" + fileName);
        // Download the file content
        try (var outputStream = new FileOutputStream(outputFile)) {
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return outputFile; // Return the downloaded file
    }


}
