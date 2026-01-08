package com.tms.position;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tms.position", "com.tms.common"})
public class PositionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionServiceApplication.class, args);
    }
}
