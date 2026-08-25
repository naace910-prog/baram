package com.wind.guild.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final ClassPathResource INDEX_HTML = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 해시 붙은 자산 (index-XXXX.js, index-XXXX.css) : 영구 캐시
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());

        // 나머지 (/, /login, /chat 등 SPA 경로 + index.html + sw.js + manifest) : 캐시 X
        // → 새 배포 시 브라우저가 반드시 최신 index.html 을 받아 새 자산 hash 로 갱신됨
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("h2-console")
                                || resourcePath.startsWith("actuator/")
                                || resourcePath.startsWith("ws/")) {
                            return null;
                        }
                        return INDEX_HTML;
                    }
                });
    }
}
