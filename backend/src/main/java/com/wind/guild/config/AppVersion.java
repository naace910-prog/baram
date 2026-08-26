package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.18";

    public static final String CHANGELOG =
            "• 레이드 카드: 참가/불참/미정을 **닉네임 나열** 로 표시 (기존 숫자만)\n" +
            "• **수동 리마인더 발송 버튼** 신설 (레이드 상세 · 문주/부문주 · 예정 상태)\n" +
            "  → 자동 리마인더 놓쳤을 때 즉시 발송 · pre30Sent 로 중복 방지";
}
