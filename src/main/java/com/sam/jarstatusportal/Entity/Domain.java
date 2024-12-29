package com.sam.jarstatusportal.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "domain")
public class Domain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String domain;          // Domain name
    private String status;          // Status (e.g., ACTIVE)
    private String expires;         // Expiration date in ISO format
    private boolean expirationProtected; // Is expiration protection enabled
    private boolean holdRegistrar;  // Is the domain on registrar hold
    private boolean locked;         // Is the domain locked
    private boolean privacy;        // Is privacy enabled
    private boolean renewAuto;      // Is auto-renew enabled
    private boolean renewable;      // Is the domain renewable
    private boolean transferProtected; // Is transfer protection enabled
    private String createdAt;       // Domain creation date in ISO format


}
