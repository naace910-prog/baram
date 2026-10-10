package com.wind.guild.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 데스크톱 앱(바람 쿨타임 도우미) Discord 로그인 중계.
 * <p>
 * 앱은 비밀값 verifier 를 만들고 그 SHA-256(challenge)만 브라우저 주소에 실어 보낸다.
 * 브라우저가 Discord 승인을 마치면 challenge 에 회원 ID 를 기록해 두고,
 * 앱이 verifier 로 물어오면 그때 앱 쪽 세션을 만들어 준다.
 * 주소(challenge)가 새어도 verifier 가 없으면 결과를 가져갈 수 없다.
 * <p>
 * 대기 중인 요청은 메모리에만 둔다 (10분 · 서버가 재시작되면 다시 로그인하면 됨).
 */
@Service
public class DesktopLoginService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_PENDING = 500;
    private static final Pattern CHALLENGE = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern VERIFIER = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public record Pending(Instant createdAt, Long memberId, String error) {
        public boolean done() { return memberId != null || error != null; }
    }

    public boolean begin(String challenge) {
        cleanup();
        if (challenge == null || !CHALLENGE.matcher(challenge).matches()) return false;
        if (pending.size() >= MAX_PENDING) return false;
        pending.put(challenge, new Pending(Instant.now(), null, null));
        return true;
    }

    /** 아직 결과가 나지 않은 요청인지 — 콜백이 데스크톱 흐름인지 가릴 때 */
    public boolean isWaiting(String challenge) {
        Pending p = challenge == null ? null : pending.get(challenge);
        return p != null && !p.done() && !expired(p);
    }

    public void complete(String challenge, Long memberId) {
        pending.computeIfPresent(challenge, (k, p) -> new Pending(p.createdAt(), memberId, null));
    }

    public void fail(String challenge, String reason) {
        pending.computeIfPresent(challenge, (k, p) -> new Pending(p.createdAt(), null, reason));
    }

    /** 앱의 확인 요청 — 결과가 났으면 한 번만 돌려주고 지운다. 모르는(만료된) 요청이면 null */
    public Pending take(String verifier) {
        if (verifier == null || !VERIFIER.matcher(verifier).matches()) return null;
        String challenge = sha256Hex(verifier);
        Pending p = pending.get(challenge);
        if (p == null) return null;
        if (expired(p)) {
            pending.remove(challenge);
            return null;
        }
        if (p.done()) pending.remove(challenge);
        return p;
    }

    private boolean expired(Pending p) {
        return p.createdAt().plus(TTL).isBefore(Instant.now());
    }

    private void cleanup() {
        pending.values().removeIf(this::expired);
    }

    static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
