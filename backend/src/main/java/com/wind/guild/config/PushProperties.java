package com.wind.guild.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "push")
@Getter @Setter
public class PushProperties {
    private String vapidPublicKey;
    private String vapidPrivateKey;
    private String subject;
}
