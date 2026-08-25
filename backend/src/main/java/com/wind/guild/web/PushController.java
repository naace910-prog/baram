package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.service.WebPushService;
import com.wind.guild.web.dto.PushDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushService pushService;

    @GetMapping("/vapid-key")
    public Map<String, Object> vapidKey() {
        return Map.of(
                "enabled", pushService.isConfigured(),
                "publicKey", pushService.getPublicKey() == null ? "" : pushService.getPublicKey());
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe(@Valid @RequestBody PushDto.SubscribeRequest req, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (memberId == null) return ResponseEntity.status(401).build();
        pushService.subscribe(memberId, req.endpoint(), req.p256dh(), req.auth());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, String>> unsubscribe(@Valid @RequestBody PushDto.UnsubscribeRequest req, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (memberId == null) return ResponseEntity.status(401).build();
        pushService.unsubscribe(memberId, req.endpoint());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }
}
