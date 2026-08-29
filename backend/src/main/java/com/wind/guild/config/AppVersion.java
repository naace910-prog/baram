package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.49";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🕐 JVM 기본 TZ 를 Asia/Seoul 로 강제 (Render UTC 문제 해결)\n" +
            "  · LocalDateTime.now() 가 KST 로 계산 → auto-complete·auto-pre30 정상 발동\n" +
            "  · 프론트가 저장하는 wall time(KST) 과 서버 비교 시각 일치\n" +
            "🔎 createNextAfterDone 성공/스킵 사유 로그 (자동 다음 raid 안 만들어질 때 원인)";
}
