package com.sam.jarstatusportal.Service;

import java.io.FileNotFoundException;
import java.io.IOException;

public interface GoogleAuthService {
    String getAccessToken() throws IOException;
}
