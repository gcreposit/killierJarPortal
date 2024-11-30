package com.sam.jarstatusportal.Entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "user_list")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String developerName;
    private String website;
    private  String port;
    private String domain;
    private String url;

    private String sslStatus;
    private String runningStatus;
    private String pid;
    private String domainLink;
    @Transient
    private MultipartFile jarFile;

    private String jarFilePath;


    private String    date ;
    private String    month ;
    private String    time ;

}
