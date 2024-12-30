package com.sam.jarstatusportal.Service;

public interface ZeroSsLService {
    String createCertificateWithAutoCSR(String domain);

    String verifyDomainByZeroSsl(String domain);
}
