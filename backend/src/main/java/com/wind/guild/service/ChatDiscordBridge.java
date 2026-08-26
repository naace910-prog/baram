package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.web.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatDiscordBridge {

    private final DiscordProperties props;
    private final ObjectProvider<DiscordBotService> botProvider;

    public boolean isEnabled() {
        return props.isEnabled()
                && props.getChatChannelId() != null && !props.getChatChannelId().isBlank();
    }

    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void relayToDiscord(ChatDto.MessageView msg) {
        if (!isEnabled()) return;
        DiscordBotService bot = botProvider.getIfAvailable();
        if (bot == null || !bot.isReady()) return;
        TextChannel ch = bot.chatChannel();
        if (ch == null) return;
        try {
            String starPrefix = msg.authorStarred() ? "⭐ " : "";
            String text = starPrefix + "**" + msg.authorNickname() + "** (사이트): " + msg.content();
            ch.sendMessage(text).queue(null, err -> log.debug("chat relay failed: {}", err.toString()));
        } catch (Exception e) {
            log.debug("chat relay error: {}", e.toString());
        }
    }
}
