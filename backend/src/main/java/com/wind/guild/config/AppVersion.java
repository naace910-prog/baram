package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.16";

    public static final String CHANGELOG =
            "• 투표 알림 · 레이드목록 · 카드에 대상 없을 때 카테고리 이름 fallback (기존 'null' / '레이드' 버그)\n" +
            "• 요일 한글 표기 (Wed → 수) — 백엔드 Locale.KOREAN + 프론트 dayjs.locale('ko')\n" +
            "• 카테고리 표시 시 아이콘(💀/🐲) 포함\n" +
            "• 30분 리마인더 안정화: 창(29~31분) → (지금~30분 이내) 로 넓힘, 카드 편집이 아닌 새 메시지로 발송 (@here), 실제 남은 분수 표기";
}
