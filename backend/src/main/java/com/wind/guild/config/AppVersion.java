package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.57";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🎯 완료 카드(득템 버튼) 미발송 진짜 원인 fix — 트랜잭션 커밋 전 발송\n" +
            "  · autoComplete 는 @Transactional, notifier 는 @Async (별도 트랜잭션)\n" +
            "  · 커밋 전에 발송을 던져서 async 가 status 를 아직 PLANNED 로 읽음\n" +
            "    → DONE 신규발송 조건 거짓 + 득템 버튼 대신 투표 버튼이 그려짐\n" +
            "  · v1.0.56 의 doneFreshSent 가 안 먹힌 이유도 이것\n" +
            "  · runAfterCommit 도입 → 커밋 완료 후 발송\n" +
            "🔔 30분 리마인더도 같은 레이스 수정\n" +
            "  · 커밋 전 발송 시 갱신된 discordMessageId 를 오래된 엔티티가 덮어쓰던 문제";
}
