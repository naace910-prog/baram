package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.15";

    public static final String CHANGELOG =
            "• 투표 알림 · 레이드목록 · 카드에 대상 없을 때 카테고리 이름 fallback (기존 'null' / '레이드' 버그)\n" +
            "• 요일 한글 표기 (Wed → 수) — 백엔드 DateTimeFormatter Locale.KOREAN + 프론트 dayjs.locale('ko')\n" +
            "• 카테고리 표시 시 아이콘(💀/🐲) 포함";
}
