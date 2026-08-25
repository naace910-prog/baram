package com.wind.guild.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wind.guild.web.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBroadcaster {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public void register(WebSocketSession s) { sessions.add(s); }
    public void unregister(WebSocketSession s) { sessions.remove(s); }
    public int connectedCount() { return sessions.size(); }

    public void broadcast(ChatDto.MessageView msg) {
        String json;
        try {
            json = mapper.writeValueAsString(msg);
        } catch (Exception e) {
            log.warn("chat json serialize failed: {}", e.toString());
            return;
        }
        TextMessage tm = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) { sessions.remove(s); continue; }
            try {
                synchronized (s) { s.sendMessage(tm); }
            } catch (Exception e) {
                log.debug("chat send failed on session {}: {}", s.getId(), e.toString());
                sessions.remove(s);
            }
        }
    }
}
