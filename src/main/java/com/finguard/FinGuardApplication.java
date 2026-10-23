package com.finguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.finguard.mapper")
public class FinGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinGuardApplication.class, args);
        System.out.println("========================================");
        System.out.println("  FinGuard-RAG is running!");
        System.out.println("  API: http://localhost:8080/api/hello");
        System.out.println("========================================");
    }
}
