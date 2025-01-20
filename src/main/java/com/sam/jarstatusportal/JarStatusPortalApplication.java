package com.sam.jarstatusportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
public class JarStatusPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(JarStatusPortalApplication.class, args);


    }

}
