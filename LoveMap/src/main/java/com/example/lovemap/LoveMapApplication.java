package com.example.lovemap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.example.lovemap.mapper")
public class LoveMapApplication {

    public static void main(String[] args) {

        SpringApplication application =
                new SpringApplication(LoveMapApplication.class);

        application.setApplicationStartup(
                new BufferingApplicationStartup(2048));

        SpringApplication.run(LoveMapApplication.class, args);
    }

}
