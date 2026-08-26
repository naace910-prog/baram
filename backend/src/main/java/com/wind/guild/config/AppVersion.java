package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.25";

    public static final String CHANGELOG =
            "• 통계 미정산: 등록 문파원 몫만 → **총 판매금 - 지급완료** 로 재정의 (미등록 인원 몫도 자동 포함)\n" +
            "• 통계 대상별 실적: 어금니 raid 안의 흑룡/묵룡/감룡/진룡도 **loot.targetId 기반 개별 집계**\n" +
            "• 대상별 표 '킬 수' → '드랍 수' 라벨\n" +
            "• **분배 · 지급 = EDIT** 로 변경 (기존 FRESH · 스팸 방지) — 최초 카드 유지, 이후는 그 카드에 편집만";
}
