package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.26";

    public static final String CHANGELOG =
            "• 통계 미정산 = **총 판매금 - 지급완료** (미등록 인원 몫도 포함)\n" +
            "• 통계 대상별 실적 = **loot.targetId 기반** (어금니 안 흑룡/묵룡/감룡/진룡 개별 카운트)\n" +
            "• Discord **모든 모달 액션 = EDIT** (기존 카드 편집 · 스팸 방지)\n" +
            "• Discord 분배 UI 개편:\n" +
            "  · [💵 판매금] 저장 후 자동으로 분배 UI 이어짐\n" +
            "  · 문파원별 개별 토글 버튼 (초록=선택 / 회색=제외)\n" +
            "  · 초기 선택: 기존 분배자 or 최신 YES 투표자\n" +
            "  · 재클릭 시 이전 선택자 pre-selected · 재분배 가능 (경고 표시)\n" +
            "  · [⚖️ N명 분배 확인] 버튼으로 실행";
}
