package com.example.lovemap.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat 配置
 * 解决 Host 头含下划线报错问题：The character [_] is never valid in a domain name
 */
@Configuration
public class TomcatConfig {

    @PostConstruct
    public void allowUnderscoreInHost() {
        System.setProperty("tomcat.util.http.parser.HttpParser.allowUnderscore", "true");
    }
}
