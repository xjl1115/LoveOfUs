package com.example.lovemap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 当 {@code file.storage=local} 时，把 {@code /uploads/**} 映射到本地存储目录。
 * <p>
 * 仅在该开关启用时注册，避免影响 OSS 模式（OSS 模式下不需要这种本地映射）。
 *
 * @author LoveMap
 */
@Configuration
@ConditionalOnProperty(name = "file.storage", havingValue = "local")
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.local.base-dir:${user.dir}/uploads}")
    private String baseDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 转成绝对路径 + 正斜杠
        String absolute = Paths.get(baseDir).toAbsolutePath().toString().replace('\\', '/');
        String location = absolute.endsWith("/") ? "file:///" + absolute : "file:///" + absolute + "/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
