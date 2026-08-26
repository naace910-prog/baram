package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.23";

    public static final String CHANGELOG =
            "• 분배 모달: **1/n 인원수 직접 편집** — 미등록 인원 포함 시 divisor 늘려서 등록 문파원 몫만 정확히 계산\n" +
            "  · 예: 판매 8,000,000전, 등록 3명 체크, 미등록 2명 → divisor 5 입력 → 1인 1,600,000전\n" +
            "  · 미등록 인원 몫은 시스템 밖에서 정산 (사이트에는 등록 문파원만 표시)";
}
