package com.thegamecellar.libraryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LibraryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryServiceApplication.class, args);
    }

}
