package com.fincore.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinCore Enterprise Banking Platform - Core Application Entrypoint.
 *
 * <p>Modular monolith foundation for core banking operations, security,
 * and identity management.</p>
 */
@SpringBootApplication
public class FinCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinCoreApplication.class, args);
    }
}

