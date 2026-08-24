package com.kheisark.ldrphotobooth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LdrPhotoboothApplication {

    public static void main(String[] args) {
        SpringApplication.run(LdrPhotoboothApplication.class, args);
    }
}
