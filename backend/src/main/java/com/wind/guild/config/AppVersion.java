package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.20";

    public static final String CHANGELOG =
            "• 참가확정 = **파티 편성 참가자 (외부인원 포함) unique** 로 재정의\n" +
            "  · 여러 역할 담당 시 이름 옆 (N) 표기 (예: 개화/마이오 (2))\n" +
            "  · N명 = unique 인원 수 (한 사람은 1명으로 카운트)\n" +
            "• 파티 카드 '총원 N명' 복원 (Discord/사이트/채팅) — 대신 unique 카운트로 정확\n" +
            "• 리마인더 임베드도 같은 규칙 적용 (buildRaidEmbed 재사용)";
}
