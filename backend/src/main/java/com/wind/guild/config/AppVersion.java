package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.21";

    public static final String CHANGELOG =
            "• 참가확정 = **파티 편성 참가자 (외부인원 포함) unique** 로 재정의\n" +
            "  · 여러 역할 담당 시 이름 옆 (N) 표기 (예: 개화/마이오 (2))\n" +
            "  · N명 = unique 인원 수\n" +
            "• 파티 카드 '총원 N명' = unique 카운트로 정확 (Discord/사이트/채팅)\n" +
            "• **주요 이벤트는 새 메시지 발송, minor 편집은 최신 카드에 편집**\n" +
            "  · 신규: 레이드 등록 · 파티편성 저장 · 파티 자동배정 · 득템 등록 · bulk 입력 · 분배 · 지급 · 30분 리마인더\n" +
            "  · 편집: 투표 · 참가확정 · 상태 · 파티 채널변경 · 개별 득템 수정\n" +
            "• 리마인더도 discordMessageId 갱신 → 이후 편집은 새 카드에";
}
