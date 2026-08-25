package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.13";

    public static final String CHANGELOG =
            "• 레이드 등록 시간 1분 단위 조정\n" +
            "• 분배금 지급 처리 (문주 스위치 · 지급자 · 지급시각 기록)\n" +
            "• 파티 자동배정 (직전 같은 대상 파티 승계, 현재 참가자만)\n" +
            "• 문주 중 '중요별표' 표시 + 채팅/디스코드 ⭐ 렌더\n" +
            "• 서버 기동 알림에 변경사항 포함";
}
