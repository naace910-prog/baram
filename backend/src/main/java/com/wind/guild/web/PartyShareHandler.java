package com.wind.guild.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 쿨타임 도우미 '파티 스킬 공유' 중계 — 방 코드로 모인 앱끼리 "누가 어떤 스킬을 썼다(남은 N초)"를 그대로 전달한다.
 * <p>
 * 로그인 없이 방 코드만으로 들어온다 (쿨타임 도우미는 문파원이 아닌 사람도 쓰므로). 그래서 내용은 최소한으로만:
 * 스킬 이름표(20자) · 남은 초 · 쿨 길이 · 보낸 사람 닉네임. 저장하지 않고 메모리에서 바로 전달만 한다.
 * <p>
 * 보호: 방 코드 형식·길이 제한, 메시지 512바이트, 사람당 초당 5개, 방당 20명, 전체 방 300개.
 */
@Slf4j
@Component
public class PartyShareHandler extends TextWebSocketHandler {

    private static final Pattern ROOM = Pattern.compile("^[A-Za-z0-9]{4,12}$");
    private static final int MAX_NAME = 16;
    private static final int MAX_KEY = 20;
    private static final int MAX_MSG_BYTES = 512;
    private static final int MAX_PER_ROOM = 20;
    private static final int MAX_ROOMS = 300;
    private static final int RATE_PER_SEC = 5;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Map<String, Member>> rooms = new ConcurrentHashMap<>();

    private record Member(WebSocketSession session, String name, long[] window) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        var q = UriComponentsBuilder.fromUri(raw.getUri()).build().getQueryParams();
        String room = decode(q.getFirst("room"));
        String name = clean(decode(q.getFirst("name")), MAX_NAME);
        if (room == null || !ROOM.matcher(room).matches() || name.isEmpty()) {
            raw.close(CloseStatus.POLICY_VIOLATION.withReason("bad room or name"));
            return;
        }
        room = room.toUpperCase();
        if (!rooms.containsKey(room) && rooms.size() >= MAX_ROOMS) {
            raw.close(CloseStatus.SERVICE_OVERLOAD.withReason("too many rooms"));
            return;
        }
        // 여러 스레드가 동시에 보내도 안전하게 (Spring WebSocketSession 은 동시 전송 불가)
        Member me = new Member(new ConcurrentWebSocketSessionDecorator(raw, 5000, 64 * 1024), name, new long[]{0, 0});
        raw.getAttributes().put("room", room);
        while (true) {
            Map<String, Member> members = rooms.computeIfAbsent(room, k -> new ConcurrentHashMap<>());
            if (members.size() >= MAX_PER_ROOM) {
                raw.close(CloseStatus.POLICY_VIOLATION.withReason("room full"));
                return;
            }
            members.put(raw.getId(), me);
            // 마지막 사람이 나가며 방을 지우는 순간과 겹쳤으면 새 방에 다시 들어간다
            if (rooms.get(room) == members) break;
            members.remove(raw.getId());
        }
        broadcastMembers(room);
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws Exception {
        String room = (String) raw.getAttributes().get("room");
        Map<String, Member> members = room == null ? null : rooms.get(room);
        Member me = members == null ? null : members.get(raw.getId());
        if (me == null || message.getPayloadLength() > MAX_MSG_BYTES || !allow(me)) return;
        JsonNode in;
        try {
            in = json.readTree(message.getPayload());
        } catch (IOException e) {
            return;
        }
        if ("bye".equals(in.path("t").asText())) {
            // 앱의 '나가기' — 앞단 프록시가 끊김을 늦게(약 10초) 알리므로 직접 알려 주면 바로 목록에서 뺀다
            raw.close(CloseStatus.NORMAL);
            return;
        }
        if (!"skill".equals(in.path("t").asText())) return;   // ping 등은 무시 (연결 유지용)
        String key = clean(in.path("k").asText(""), MAX_KEY);
        double left = in.path("l").asDouble(-1);
        double dur = in.path("d").asDouble(0);
        if (key.isEmpty() || left < 0 || left > 3600 || dur < 0 || dur > 3600) return;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("t", "skill");
        out.put("k", key);
        out.put("l", Math.round(left * 10) / 10.0);
        out.put("d", Math.round(dur * 10) / 10.0);
        out.put("from", me.name());
        TextMessage msg = new TextMessage(json.writeValueAsString(out));
        for (Member m : members.values()) {
            if (m != me) send(m, msg);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession raw, CloseStatus status) {
        String room = (String) raw.getAttributes().get("room");
        if (room == null) return;
        Map<String, Member> members = rooms.get(room);
        if (members == null) return;
        members.remove(raw.getId());
        if (members.isEmpty()) {
            rooms.remove(room, members);
        } else {
            broadcastMembers(room);
        }
    }

    private void broadcastMembers(String room) {
        Map<String, Member> members = rooms.get(room);
        if (members == null) return;
        List<String> names = new ArrayList<>();
        members.values().forEach(m -> names.add(m.name()));
        try {
            TextMessage msg = new TextMessage(json.writeValueAsString(Map.of("t", "members", "names", names)));
            members.values().forEach(m -> send(m, msg));
        } catch (IOException ignored) {
        }
    }

    private void send(Member m, TextMessage msg) {
        try {
            if (m.session().isOpen()) m.session().sendMessage(msg);
        } catch (Exception e) {
            log.debug("party send failed: {}", e.getMessage());
        }
    }

    /** 1초 창 안에서 RATE_PER_SEC 개까지 */
    private boolean allow(Member m) {
        long now = System.currentTimeMillis() / 1000;
        synchronized (m.window()) {
            if (m.window()[0] != now) {
                m.window()[0] = now;
                m.window()[1] = 0;
            }
            return ++m.window()[1] <= RATE_PER_SEC;
        }
    }

    private static String decode(String v) {
        return v == null ? null : java.net.URLDecoder.decode(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String clean(String v, int max) {
        if (v == null) return "";
        String s = v.replaceAll("[\\p{Cntrl}<>]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
