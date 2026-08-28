package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.41";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🆕 완료된 레이드와 같은 대상으로 **다음 레이드 자동 등록** (자동 완료 or 수동 완료)\n" +
            "  · scheduledAt = null (시간 미정) 로 생성 · 이미 대기중인 같은 대상 있으면 skip\n" +
            "🕐 Discord 카드에 [🕐 시간 입력] 버튼 (시간 미정 raid 만 · 문주/부문주)\n" +
            "  · 기본값: 다음날 00:00 · yyyy-MM-dd HH:mm 형식\n" +
            "🛡️ 레이드 생성 시 **본대 파티 1개 자동 생성** (수동 등록/자동 등록 모두)\n" +
            "• raid.scheduledAt DB nullable 마이그레이션 (시간 미정 지원)\n" +
            "• 사이트: 시간 미정 raid = '⏳ 시간 미정' 표시 (대시보드/목록/상세)";
}
