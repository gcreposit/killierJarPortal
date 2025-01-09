package com.sam.jarstatusportal.Service;

import com.google.cloud.storage.Storage;
import com.jcraft.jsch.JSchException;
import com.sam.jarstatusportal.Entity.User;

import java.io.IOException;
import java.util.List;

public interface JarService {


    String uplaodingAllFormData(User user) throws IOException;

    List<User> findAllJarsData();

    List<User> findAllDataOfUser(Long id);

    String updationJarFile(User user);

    void savePidIntoDatabase(String pid, Long id);

    String getPidById(Long id);

    String generateSignedUrl(User user, String fileName) throws IOException;

    void finalizeUpload(User user) throws IOException, JSchException;

    Storage getInitializeStorage() throws IOException;
}
