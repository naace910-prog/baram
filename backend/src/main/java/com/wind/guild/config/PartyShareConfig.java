package com.wind.guild.config;

import com.wind.guild.web.PartyShareHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** 쿨타임 도우미 파티 스킬 공유 — wss://<사이트>/ws/party?room=코드&name=닉네임 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class PartyShareConfig implements WebSocketConfigurer {

    private final PartyShareHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 데스크톱 앱이 접속하므로 브라우저 Origin 검사는 하지 않는다 (방 코드가 입장 조건)
        registry.addHandler(handler, "/ws/party").setAllowedOriginPatterns("*");
    }
}
