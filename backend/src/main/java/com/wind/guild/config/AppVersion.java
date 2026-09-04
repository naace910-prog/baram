package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.52";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "📋 Discord API 호출/응답 로그 테이블 신설 (discord_api_logs)\n" +
            "  · 모든 send/edit 의 성공·실패·latency·에러문구·msgId 를 DB 에 기록\n" +
            "  · 대상: raidCard·raidCardFresh·raidCardFallback·raidPre30·lootCard·alert\n" +
            "🩺 설정 → Discord 진단 강화\n" +
            "  · JDA status·gateway ping·429 cooldown 남은 초 표시\n" +
            "  · [최근 로그] 버튼: 최근 100건 + 1시간 성공/실패 집계\n" +
            "  · [Cooldown 해제] 버튼 (문주 전용)";
}
