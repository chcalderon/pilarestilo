package com.pilarestilo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PilarEstiloApplication {

    public static void main(String[] args) {
        SpringApplication.run(PilarEstiloApplication.class, args);
    }
}
