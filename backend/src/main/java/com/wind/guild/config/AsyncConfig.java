package com.wind.guild.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Discord/Push 등 외부 I/O 를 백그라운드에서 처리.
     * 백엔드 API 응답이 외부 호출을 기다리지 않도록.
     */
    @Bean(name = "discordExecutor")
    public Executor discordExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("discord-async-");
        ex.setKeepAliveSeconds(60);
        ex.initialize();
        return ex;
    }
}
