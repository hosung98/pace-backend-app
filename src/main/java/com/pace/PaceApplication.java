package com.pace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/** PACE 백엔드 애플리케이션의 시작점입니다. */
public class PaceApplication {
    /** Spring Boot 애플리케이션을 부팅합니다. */
    public static void main(String[] args) {
        SpringApplication.run(PaceApplication.class, args);
    }
}
