package com.oms.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * com.oms.common is scanned explicitly so the shared GlobalExceptionHandler,
 * JWT components and logging filters are registered in this service.
 */
@SpringBootApplication(scanBasePackages = {"com.oms.user", "com.oms.common"})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
