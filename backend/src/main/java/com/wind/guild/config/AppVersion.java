package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.44";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🐛 최초등록·재발송 실패 hotfix\n" +
            "  · CREATED trigger 는 무조건 새 메시지 (edit path 의 @Async race 우회)\n" +
            "  · force-resend: raidRepo.save 대신 targeted update (async 이전 커밋 보장)\n" +
            "  · 모든 send·edit·fallback 성공/실패 INFO/WARN 로그 (원인 추적 가능)";
}
