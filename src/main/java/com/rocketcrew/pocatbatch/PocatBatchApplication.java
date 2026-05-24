package com.rocketcrew.pocatbatch;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PocatBatchApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> {
            // OS 환경변수가 이미 존재하면 덮어쓰지 않음
            if (System.getenv(e.getKey()) == null) {
                System.setProperty(e.getKey(), e.getValue());
            }
        });
        SpringApplication.run(PocatBatchApplication.class, args);
    }
}
