package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.47";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🎨 로딩 인디케이터 UX 개선\n" +
            "  · 상단 얇은 progress bar → 화면 중앙 빙글빙글 스피너 (antd Spin)\n" +
            "  · 150ms 이내 짧은 요청은 표시 안 함 (깜빡임 방지)\n" +
            "  · axios 인터셉터 그대로 → 모든 API 요청에 자동 적용";
}
