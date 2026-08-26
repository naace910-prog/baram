package com.wind.guild.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wind.guild.config.PushProperties;
import com.wind.guild.domain.PushSubscription;
import com.wind.guild.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WebPushService {

    private final PushProperties props;
    private final PushSubscriptionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private PushService pushClient;

    @PostConstruct
    public void init() {
        Security.addProvider(new BouncyCastleProvider());
        if (isConfigured()) {
            try {
                pushClient = new PushService(props.getVapidPublicKey(), props.getVapidPrivateKey(), props.getSubject());
                log.info("Web Push 초기화 완료");
            } catch (Exception e) {
                log.warn("Web Push 초기화 실패: {}", e.toString());
            }
        } else {
            log.info("Web Push 미설정 (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY 없음)");
        }
    }

    public boolean isConfigured() {
        return props.getVapidPublicKey() != null && !props.getVapidPublicKey().isBlank()
                && props.getVapidPrivateKey() != null && !props.getVapidPrivateKey().isBlank();
    }

    public String getPublicKey() { return props.getVapidPublicKey(); }

    public void subscribe(Long memberId, String endpoint, String p256dh, String auth) {
        var existing = repo.findByEndpoint(endpoint);
        if (existing.isPresent()) {
            existing.get().setMemberId(memberId);
            existing.get().setP256dh(p256dh);
            existing.get().setAuth(auth);
        } else {
            repo.save(PushSubscription.builder()
                    .memberId(memberId).endpoint(endpoint).p256dh(p256dh).auth(auth)
                    .build());
        }
    }

    public void unsubscribe(Long memberId, String endpoint) {
        repo.deleteByMemberIdAndEndpoint(memberId, endpoint);
    }

    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void sendToAll(String title, String body, String url) {
        if (pushClient == null) return;
        String payload = buildPayload(title, body, url);
        if (payload == null) return;
        for (PushSubscription s : repo.findAll()) sendOne(s, payload);
    }

    @org.springframework.scheduling.annotation.Async("discordExecutor")
    public void sendToMember(Long memberId, String title, String body, String url) {
        if (pushClient == null) return;
        String payload = buildPayload(title, body, url);
        if (payload == null) return;
        for (PushSubscription s : repo.findByMemberId(memberId)) sendOne(s, payload);
    }

    private String buildPayload(String title, String body, String url) {
        try {
            return mapper.writeValueAsString(Map.of(
                    "title", title == null ? "알림" : title,
                    "body", body == null ? "" : body,
                    "url", url == null ? "/" : url));
        } catch (Exception e) { return null; }
    }

    private void sendOne(PushSubscription s, String payload) {
        try {
            var notif = new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(), payload.getBytes());
            var resp = pushClient.send(notif);
            int code = resp.getStatusLine().getStatusCode();
            if (code == 404 || code == 410) {
                repo.deleteById(s.getId());
                log.debug("만료된 push 구독 삭제: {}", s.getId());
            } else if (code >= 400) {
                log.debug("push 실패 code={} sub={}", code, s.getId());
            }
        } catch (Exception e) {
            log.debug("push 전송 실패 sub={}: {}", s.getId(), e.toString());
        }
    }
}
