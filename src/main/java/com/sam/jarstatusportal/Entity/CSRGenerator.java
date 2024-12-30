package com.sam.jarstatusportal.Entity;


import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;


public class CSRGenerator {

    public static PKCS10CertificationRequest generateCSR(KeyPair keyPair, String distinguishedName)
            throws OperatorCreationException {
        X500Principal subject = new X500Principal(distinguishedName);
        PKCS10CertificationRequestBuilder p10Builder =
                new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
        ContentSigner signer = csBuilder.build(keyPair.getPrivate());

        return p10Builder.build(signer);
    }
    public static String convertToPEM(PKCS10CertificationRequest csr) throws IOException {
        StringWriter sw = new StringWriter();
        try (org.bouncycastle.openssl.jcajce.JcaPEMWriter pemWriter = new org.bouncycastle.openssl.jcajce.JcaPEMWriter(sw)) {
            pemWriter.writeObject(csr);
        }
        return sw.toString();
    }
}
