package com.wind.guild.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "discord")
@Getter @Setter
public class DiscordProperties {
    private boolean enabled;
    private String botToken;
    private String guildId;
    private String notifyChannelId;
    private String chatChannelId;
    private String webhookUrl;
    private String siteBaseUrl;
    private String clientId;
    private String clientSecret;
    private String oauthRedirectUri;
    private String oauthLoginSuccessRedirect;
}
