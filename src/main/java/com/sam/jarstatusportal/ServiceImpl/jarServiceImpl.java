package com.sam.jarstatusportal.ServiceImpl;

import com.sam.jarstatusportal.Entity.User;
import com.sam.jarstatusportal.Repository.JarRepo;
import com.sam.jarstatusportal.Service.JarService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    @Override
    public String uplaodingAllFormData(User user) throws IOException {

        MultipartFile jarFile = user.getJarFile();

        Path jarFilePath = Path.of(jarUplaodDirectory +"/"+ jarFile.getOriginalFilename());

        Files.createDirectories(jarFilePath.getParent());
        jarFile.transferTo(jarFilePath);
        user.setJarFilePath(String.valueOf(jarFilePath));


        jarRepo.save(user);
        return "data saved successfully";
    }

    @Override
    public List<User> findAllJarsData() {
        return jarRepo.findAll();
    }

    @Override
    public List<User> findAllDataOfUser(Long id) {
        List<User> list= jarRepo.findByyId(id);
        return  list;
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


//                        MultipartFile multipartFile=user.getJarFile();
//
////                        MultipartFile multipartFile = nonResidential.getStructurePicNr();
//
//                        if (multipartFile != null && !multipartFile.isEmpty()) {
//
//                            String fileName = multipartFile.getOriginalFilename();
//                            String[] name = fileName.split("\\.");
//                            int n = name.length - 1;
//                            String originalName = nonResidential.getBuildingDetail() + "("+timestampMillis.toString()+")"+ "." + name[n];
//
//                            String filePath = uploadDirectory + "NonResidential/" + originalName;
//
//                            Files.createDirectories(Path.of(filePath).getParent());
//
//                            multipartFile.transferTo(new File(filePath));
//
//                            existingNonResidential.setUploadPicPath(originalName);
//
//                        }



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

        }
        else {
            return null; // Or handle the case where the NonResidential object with the given ID was not found
        }
        return null;
    }
}
