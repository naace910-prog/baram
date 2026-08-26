package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.24";

    public static final String CHANGELOG =
            "• 분배 모달: **1/n 인원수 직접 편집** — 미등록 인원 포함 시 divisor 늘려서 계산\n" +
            "• Discord 완료 카드에 **드랍별 상태 액션 버튼** 추가:\n" +
            "  · 판매금 없음 → [💵 이름] → 판매금 입력 모달\n" +
            "  · 판매금 있음, 미분배 → [⚖️ 이름] → 분배 모달 (인원수 편집)\n" +
            "  · 분배완료 → [✅ 이름] disabled (재분배는 사이트에서)\n" +
            "• 상단 [➕ 대상 추가] 라벨 명확화 (기존 '드랍' 이 헷갈렸음)\n" +
            "• 분배 모달 참가자는 파티 편성 등록 문파원 자동 산정 (외부인원은 divisor 로 반영)";
}
