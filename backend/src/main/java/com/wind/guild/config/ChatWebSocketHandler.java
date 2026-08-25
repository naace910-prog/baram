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
        broadcaster.register(session);
        log.info("chat ws connected: sessionId={} memberId={} (total={})",
                session.getId(), memberId, broadcaster.connectedCount());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
        log.info("chat ws closed: sessionId={} status={} (total={})",
                session.getId(), status, broadcaster.connectedCount());
    }
}
