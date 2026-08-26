package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.19";

    public static final String CHANGELOG =
            "• 파티 '총원 N명' 표시 제거 (Discord 카드 / 사이트 파티 / 채팅 요약) — 한 사람 2역 시 부풀려지던 문제\n" +
            "• 30분 리마인더 메시지에 **레이드 카드 임베드** 첨부 (참가자·파티·득템 포함, 파티 편성 양식 그대로)";
}
