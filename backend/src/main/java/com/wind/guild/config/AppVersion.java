package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.40";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🔧 hotfix: Discord 서버 기동 알림 미발송\n" +
            "  · CHANGELOG 누적으로 Discord 2000자 제한 초과 → 알림 실패\n" +
            "  · 정책: CHANGELOG 는 이번 배포만 담기 (누적 X)\n" +
            "  · DiscordBotService 기동 알림 문구 자동 truncate 안전장치 추가\n" +
            "• 🔄 Discord 카드 강제 재발송 버튼 (raid 상세, 문주 전용)\n" +
            "  · 최초 발송 실패/카드 유실 시 회복 (raid.discordMessageId=null 후 재발송)\n" +
            "• 대시보드/레이드 목록: 카드 자체 클릭으로 상세 진입 (기존 '상세' 버튼 제거)";
}
