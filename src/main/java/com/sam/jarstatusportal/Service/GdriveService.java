package com.sam.jarstatusportal.Service;

import io.opencensus.resource.Resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public interface GdriveService {




    String findLatestFile(String projectName, String fileType) throws IOException;

    File downloadFile(String fileId) throws IOException;
}
