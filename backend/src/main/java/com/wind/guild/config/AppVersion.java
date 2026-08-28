package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.43";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🕐 모바일 레이드 시간 편집 UI (DatePicker 모달)\n" +
            "🩺 설정 → Discord 봇 진단 버튼 (봇 상태 + 채널 도달 + 테스트 발송)\n" +
            "🔎 syncRaidCard 봇 미준비 시 warn 로그 (카드 재발송 무응답 원인 추적용)";
}
