package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.22";

    public static final String CHANGELOG =
            "• Discord **완료 카드에 대상별 [드랍 등록] 버튼** 추가 (문주/부문주 전용)\n" +
            "  · 해골왕: [💀 해골왕 드랍] 1개\n" +
            "  · 어금니: [🐲 흑룡] [🐲 묵룡] [🐲 감룡] [🐲 진룡] 4개\n" +
            "• 클릭 → Discord 모달 팝업: 수량 · 1개당 가격 입력 → 등록\n" +
            "• 같은 드랍 여러 개는 수량으로 (흑룡 2개 = 수량 2)\n" +
            "• 등록 완료 시 카드 새로 발송 + 채팅에 시스템 메시지";
}
