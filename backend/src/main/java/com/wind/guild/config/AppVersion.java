package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.27";

    public static final String CHANGELOG =
            "🔧 hotfix: 판매금 모달 TextInput.setValue('') 예외 (판매금 null 인 경우)\n" +
            "\n" +
            "• 통계 미정산 = **총 판매금 - 지급완료** (미등록 인원 몫도 포함)\n" +
            "• 통계 대상별 실적 = **loot.targetId 기반** (어금니 안 흑룡/묵룡/감룡/진룡 개별 카운트)\n" +
            "• Discord 완료 카드 loot 라인별 **3단계 버튼** 재정리:\n" +
            "  · 판매금 없음: [💵 이름] → 판매금 모달 (숫자 1개)\n" +
            "  · 판매금 있음, 미분배: [⚖️ 이름] → 분배 모달 (÷ N명 divisor 입력)\n" +
            "  · 분배됨: [💰 이름] → **지급 관리 UI** (사람별 개별 토글)\n" +
            "     - 초록 = 지급됨 / 회색 = 미지급\n" +
            "     - 클릭 즉시 저장 (자동 반영)\n" +
            "     - 재클릭 시 이전 지급 상태 그대로 보임\n" +
            "• Discord 모든 액션 = EDIT (스팸 방지)";
}
