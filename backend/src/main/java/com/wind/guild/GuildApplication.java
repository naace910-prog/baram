package com.wind.guild;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class GuildApplication {
    public static void main(String[] args) {
        // Render 기본 TZ=UTC → 프론트/사용자가 사용하는 KST 와 9시간 어긋남.
        // LocalDateTime.now() 가 스케줄러(auto-complete·auto-pre30) 판정에 쓰이므로
        // JVM 기본 TZ 를 Asia/Seoul 로 강제해서 프론트가 저장하는 wall time 과 일치시킨다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        System.setProperty("user.timezone", "Asia/Seoul");
        SpringApplication.run(GuildApplication.class, args);
    }
}
