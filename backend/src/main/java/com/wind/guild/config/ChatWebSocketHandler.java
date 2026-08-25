package com.wind.guild.config;

import com.wind.guild.service.ChatBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatBroadcaster broadcaster;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object memberId = session.getAttributes().get(SessionKeys.MEMBER_ID);
        if (memberId == null) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE.withReason("로그인 필요")); } catch (Exception ignored) {}
            return;
        }
        broadcaster.register(session);
        log.debug("chat ws connected: sessionId={} memberId={}", session.getId(), memberId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
