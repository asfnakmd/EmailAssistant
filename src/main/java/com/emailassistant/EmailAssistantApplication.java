package com.emailassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmailAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailAssistantApplication.class, args);
    }
}
