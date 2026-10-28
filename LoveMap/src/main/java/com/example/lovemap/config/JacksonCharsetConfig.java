package com.example.lovemap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局 JSON 响应声明 UTF-8 字符集。
 *
 * <p>Spring Boot 默认的 {@link MappingJackson2HttpMessageConverter} 输出
 * {@code Content-Type: application/json}，未带 charset；当浏览器收到这类响应时，
 * 会按当前页面/系统默认编码（如 GBK）解码，导致中文字段显示为乱码。
 *
 * <p>顾问台（vip-admin.html）等纯 HTML 页面通过 {@code fetch().json()} 取数据，
 * 浏览器以响应头里的编码为准，所以最干净的修法是让 JSON 响应自带
 * {@code charset=UTF-8}。
 */
@Configuration
public class JacksonCharsetConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
        for (org.springframework.http.converter.HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                // 同时把支持的媒体类型声明带 charset，避免部分客户端按 GBK 解析
                jacksonConverter.setSupportedMediaTypes(List.of(
                        new MediaType("application", "json", StandardCharsets.UTF_8),
                        MediaType.APPLICATION_JSON
                ));
            }
        }
    }
}