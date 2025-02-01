package com.sam.jarstatusportal.ServiceImpl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Repository.JarRepo;
import com.sam.jarstatusportal.Service.JarService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class jarServiceImpl implements JarService {


    @Autowired
    private JarRepo jarRepo;

    @Value("${upload.directory}")
    public String jarUplaodDirectory;


    public String vmJarUplaodDirectory = "C:\\Users\\Administrator\\Desktop\\JarTesting\\";


    @Value("${vm.remoteJarUploadPath}")
    private String remoteJarUploadPath;

    @Value("${vm.ip}")
    private String vmIp;

    @Value("${vm.userName}")
    private String vmUserName;

    @Value("${vm.password}")
    private String vmPassword;



    private Map<String, User> temporaryStorage = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(JarService.class);

    private static final String BUCKET_NAME = "gpdm_bucket";

    @Override
    public String uplaodingAllFormData(User user) throws IOException {

        MultipartFile jarFile = user.getJarFile();

        // Initialize GCS Storage
//        Storage storage = StorageOptions.getDefaultInstance().getService();

//        Storage storage = initializeStorage();
//        String bucketName = "gpdm_bucket";
        String gcsFilePath = "uploaded-jars/" + jarFile.getOriginalFilename();


//        Signed Url Methodology
        String signedUrl = generateSignedUrl(BUCKET_NAME, gcsFilePath);
        // Upload the file to GCS using the signed URL
        uploadFileToGcsUsingSignedUrl(jarFile, signedUrl);


        // Upload file to GCS

        user.setJarFilePath(vmJarUplaodDirectory + jarFile.getOriginalFilename());

        jarRepo.save(user);

        // Download file from GCS for transfer to VM
        java.io.File tempFile = downloadFromGCS(BUCKET_NAME, gcsFilePath);

        // Transfer file to the VM
        try {
            transferFileToVM(tempFile.getAbsolutePath(), remoteJarUploadPath + "/" + jarFile.getOriginalFilename(),
                    vmUserName, vmIp, vmPassword);
            logger.info("JAR file successfully uploaded to VM.");
//            tempFile.delete(); // Clean up temporary file
        } catch (Exception e) {
            logger.error("File transfer to VM failed: " + e.getMessage(), e);
            throw new IOException("Failed to transfer the file to the VM", e);
        }

        return "Data saved and JAR file uploaded to VM successfully";

    }

    /**
     * Generates a signed URL for uploading a file to GCS.
     */
    private String generateSignedUrl(String bucketName, String objectName) throws StorageException, IOException {
        Storage storage = initializeStorage();

        // Create BlobInfo for the object
        // Specify the Content-Type explicitly
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType("application/octet-stream") // Set the Content-Type for binary file
                .build();

        // Generate a signed URL with HTTP PUT method and expiration time
        String signedUrl = storage.signUrl(
                blobInfo,
                15, // Expiration time in minutes
                TimeUnit.MINUTES,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT), Storage.SignUrlOption.withContentType() // Specify HTTP method as PUT
        ).toString();

        logger.info("Generated signed URL for object '{}': {}", objectName, signedUrl);

        return signedUrl;
    }


    /**
     * Uploads a file to GCS using a signed URL.
     */
    private void uploadFileToGcsUsingSignedUrl(MultipartFile jarFile, String signedUrl) throws IOException {
        logger.info("Uploading file '{}' to GCS using signed URL in chunks.", jarFile.getOriginalFilename());
        java.net.URL url = new java.net.URL(signedUrl);

        // Open an HTTP connection to the signed URL
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT"); // Ensure the HTTP method matches the signed URL
        connection.setRequestProperty("Content-Type", "application/octet-stream"); // Content-Type for binary file
        connection.setChunkedStreamingMode(5 * 1024 * 1024); // Set chunk size to 5 MB

        try (InputStream inputStream = jarFile.getInputStream();
             OutputStream outputStream = connection.getOutputStream()) {
            byte[] buffer = new byte[8192]; // 8 KB buffer
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            logger.info("Chunk uploaded successfully.");
        } catch (IOException e) {
            logger.error("Error writing file to signed URL: {}", e.getMessage(), e);
            throw e;
        }

        // Verify the server's response code
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                StringBuilder errorMessage = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    errorMessage.append(line);
                }
                logger.error("Failed to upload file. Response code: {}. Error message: {}", responseCode, errorMessage);
            }
            throw new IOException("Failed to upload file to GCS. Response code: " + responseCode);
        }

        logger.info("File '{}' uploaded successfully in chunks via signed URL.", jarFile.getOriginalFilename());
    }


    // -----------------------------   This method is to Download the File From Google Cloud Bucket Storage-------------------------------
    private java.io.File downloadFromGCS(String bucketName, String gcsFilePath) throws IOException {

        // Initialize GCS Storage
        Storage storage = initializeStorage();

        BlobId blobId = BlobId.of(bucketName, gcsFilePath);
        Blob blob = storage.get(blobId);

        if (blob == null) {
            throw new IOException("File not found in GCS: " + gcsFilePath);
        }

        // Ensure the file name is valid for the local file system
        String sanitizedFileName = gcsFilePath.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

        // Create a temporary file to store the blob content
        java.io.File tempFile = java.io.File.createTempFile("temp-", sanitizedFileName);
        blob.downloadTo(tempFile.toPath());
        logger.info("File downloaded from GCS to: " + tempFile.getAbsolutePath());
        return tempFile;
    }


    @Override
    public List<User> findAllJarsData() {
        return jarRepo.findAll();
    }

    @Override
    public List<User> findAllDataOfUser(Long id) {
        List<User> list = jarRepo.findByyId(id);
        return list;
    }

    @Override
    public String updationJarFile(User user) {


        Optional<User> optionalNonResidential = jarRepo.findById(user.getId());

        if (optionalNonResidential.isPresent()) {
            User existingJarData = optionalNonResidential.get();
            Map<String, Object> originalFieldValues = new HashMap<>(); // Moved inside the if block

            // Get all fields using reflection
            Field[] fields = User.class.getDeclaredFields();

            for (Field field : fields) {
                // Make private fields accessible
                field.setAccessible(true);

                try {

                    // Get the value of the field from the nonResidential object
                    Object newValue = Optional.ofNullable(field.get(user)).orElse(field.get(existingJarData));
                    // Get the value of the field from the existingNonResidential object
                    Object existingValue = field.get(existingJarData);

                    // Compare the values and update if they are different
                    if (!Objects.equals(existingValue, newValue)) {
                        // Store original value
                        originalFieldValues.put(field.getName(), existingValue);
                        // Set the new value to the field of existingNonResidential
                        field.set(existingJarData, newValue);


                        MultipartFile jarFile = user.getJarFile();
                        if (jarFile != null && !jarFile.isEmpty()) {
                            // Define the path for the file
                            Path jarFilePath = Path.of(jarUplaodDirectory + "/" + jarFile.getOriginalFilename());

                            // Check if the file already exists
                            if (Files.exists(jarFilePath)) {
                                // Delete the existing file
                                Files.delete(jarFilePath);
                            }

                            // Create directories if they do not exist
                            Files.createDirectories(jarFilePath.getParent());

                            // Save the new file to the specified path
                            jarFile.transferTo(jarFilePath.toFile());

                            // Set the file path in the user object
                            existingJarData.setJarFilePath(jarFilePath.toString());
                        }

                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    // Handle the exception as needed
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            // Save and return the updated nonResidential object
            jarRepo.save(existingJarData);

        } else {
            return null; // Or handle the case where the NonResidential object with the given ID was not found
        }
        return null;
    }

    @Override
    public void savePidIntoDatabase(String pid, Long id) {

        User user = jarRepo.findById(id).get();
        user.setPid(pid);
        jarRepo.save(user);

    }

    @Override
    public String getPidById(Long id) {

        String pid = jarRepo.findById(id).get().getPid();
        return pid;
    }

    //    --------------------------new Implementation Started----------------------------------------------------------------------------

    @Override
    public String generateSignedUrl(User user, String fileName) throws IOException {
        String objectName = "uploaded-jars/" + fileName;
        logger.info("Generating signed URL for user: {} with file name: {}", user.getDeveloperName(), fileName);

        saveTemporaryUserData(user, fileName);

        // Generate the signed URL
        String signedUrl = generateSignedUrl(BUCKET_NAME, objectName);

        // Log the generated signed URL
        logger.info("Generated signed URL for object: {} is {}", objectName, signedUrl);

        return signedUrl;
    }

    //    -------------------------Finalize upload  Started ---------------------------------------------------------------------------------------

    @Override
    public void finalizeUpload(User user) throws IOException, JSchException {

        logger.info("filePath: {}", user.getBucketFilePath());
        // Extract the required portion (_dev4.jar) from the filePath
//        String extractedFileName = filePath.substring(filePath.lastIndexOf("/") + 1);
        logger.info("Extracted file name: {}", user.getOriginalFileName());

        java.io.File downloadedFile = downloadFromGCS(BUCKET_NAME, user.getOriginalFileName());

//        User user = getTemporaryUserData(extractedFileName); // Retrieve temporary data

        // Transfer the file to the VM
        transferFileToVM(downloadedFile.getAbsolutePath(), remoteJarUploadPath + "/" + user.getOriginalFileName(),
                vmUserName, vmIp, vmPassword);


        if (user.getId() != null ) {
            Optional<User> optionalNonResidential = jarRepo.findById(user.getId());

            if (optionalNonResidential.isPresent()) {
                User existingJarData = optionalNonResidential.get();
                Map<String, Object> originalFieldValues = new HashMap<>(); // Moved inside the if block

                // Get all fields using reflection
                Field[] fields = User.class.getDeclaredFields();

                for (Field field : fields) {
                    // Make private fields accessible
                    field.setAccessible(true);

                    try {

                        // Get the value of the field from the nonResidential object
                        Object newValue = Optional.ofNullable(field.get(user)).orElse(field.get(existingJarData));
                        // Get the value of the field from the existingNonResidential object
                        Object existingValue = field.get(existingJarData);

                        // Compare the values and update if they are different
                        if (!Objects.equals(existingValue, newValue)) {
                            // Store original value
                            originalFieldValues.put(field.getName(), existingValue);
                            // Set the new value to the field of existingNonResidential
                            field.set(existingJarData, newValue);


//                        MultipartFile jarFile = user.getJarFile();
//                        if (jarFile != null && !jarFile.isEmpty()) {
//                            // Define the path for the file
//                            Path jarFilePath = Path.of(jarUplaodDirectory + "/" + jarFile.getOriginalFilename());
//
//                            // Check if the file already exists
//                            if (Files.exists(jarFilePath)) {
//                                // Delete the existing file
//                                Files.delete(jarFilePath);
//                            }
//
//                            // Create directories if they do not exist
//                            Files.createDirectories(jarFilePath.getParent());
//
//                            // Save the new file to the specified path
//                            jarFile.transferTo(jarFilePath.toFile());
//
//                            // Set the file path in the user object
//                            existingJarData.setJarFilePath(jarFilePath.toString());
//                        }




                        }
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        // Handle the exception as needed
                    }
                }

                existingJarData.setJarFilePath(vmJarUplaodDirectory + user.getOriginalFileName());
                // Save and return the updated nonResidential object
                jarRepo.save(existingJarData);

            }
        }


        else{
            user.setJarFilePath(vmJarUplaodDirectory + user.getOriginalFileName());

            // Save user data to the database
            jarRepo.save(user);
        }







        logger.info("User data saved to the database for filePath: {}", user.getBucketFilePath());

        logger.info("finalizeUpload completed successfully for filePath: {}", user.getBucketFilePath());
    }

    @Override
    public Storage getInitializeStorage() throws IOException {

        Storage getStorage = initializeStorage();
        return getStorage;
    }

    //    -------------------------Finalize upload  Ended ---------------------------------------------------------------------------------------


    private void saveTemporaryUserData(User user, String fileName) {
        user.setOriginalFileName(fileName);
        temporaryStorage.put(fileName, user);
    }

    private User getTemporaryUserData(String filePath) {
        return temporaryStorage.get(filePath);
    }
    //    --------------------------new Implementation Ended-----------


    // -----------------------------   This method is to transfer the JAR File to VM--------------------------------
    private void transferFileToVM(String localFilePath, String remoteFilePath, String username, String host, String password) throws JSchException, IOException {
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
            logger.info("SCP channel opened.");

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
            logger.info("File transfer completed.");

        } catch (Exception e) {
            logger.error("Error during SCP file transfer: " + e.getMessage(), e);
            throw e;
        } finally {
            // Clean up resources
            if (channelExec != null && channelExec.isConnected()) {
                channelExec.disconnect();
                logger.info("SCP channel closed.");
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
                logger.info("SSH session disconnected.");
            }
        }
    }

    // -----------------------------   This method is to Initialize the Google Cloud Bucket Storage-------------------------------
    private Storage initializeStorage() throws IOException {
        InputStream jsonStream;
        String externalFilePath = "/app/resources/jalshakti-1734933826605-883dbf306dbe.json";
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
                throw new FileNotFoundException("Service account JSON file not found in classpath or Docker path.");
            }
            logger.info("Loaded service account JSON file from classpath.");
        }

        // Create credentials from the JSON file
        GoogleCredentials credentials = GoogleCredentials.fromStream(jsonStream)
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

        // Initialize the Storage client with the Project ID
        Storage storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()
                .getService();

        logger.info("Google Cloud Storage client initialized successfully for project: " + projectId);
        return storage;
    }

}
