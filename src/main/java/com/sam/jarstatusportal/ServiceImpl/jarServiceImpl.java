package com.sam.jarstatusportal.ServiceImpl;

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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

    private static final Logger logger = LoggerFactory.getLogger(JarService.class);


    @Override
    public String uplaodingAllFormData(User user) throws IOException {

        MultipartFile jarFile = user.getJarFile();

        Path jarFilePath = Path.of(jarUplaodDirectory + "/" + jarFile.getOriginalFilename());

        Files.createDirectories(jarFilePath.getParent());
        jarFile.transferTo(jarFilePath);
        user.setJarFilePath(vmJarUplaodDirectory + jarFilePath.getFileName());


        jarRepo.save(user);


        // Transfer the file to the VM
        try {
            transferFileToVM(jarFilePath.toString(), remoteJarUploadPath + "/" + jarFile.getOriginalFilename(), vmUserName, vmIp, vmPassword);
            logger.info("JAR file successfully uploaded to VM.");
        } catch (Exception e) {
            logger.error("File transfer to VM failed: " + e.getMessage(), e);
            throw new IOException("Failed to transfer the file to the VM", e);
        }

        return "Data saved and JAR file uploaded to VM successfully";

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

}
