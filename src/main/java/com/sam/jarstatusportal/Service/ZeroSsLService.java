package com.sam.jarstatusportal.Service;

import java.io.IOException;

public interface ZeroSsLService {
    String createCertificateWithAutoCSR(String domain);

    String verifyDomainByZeroSsl(String domain);

    String downlaodZeroSslCertificate(String domain) throws IOException;
}
