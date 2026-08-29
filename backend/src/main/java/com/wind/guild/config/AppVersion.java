package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.46";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🐛 파티 저장·모달 UX fix\n" +
            "  · saveParty·saveAll silent catch → 실제 에러 message 표출 (원인 파악)\n" +
            "  · 외부인원 입력 모달: Enter=확인 (state 기반 Modal + onPressEnter)\n" +
            "  · 새 파티 생성 모달: Enter=생성 (Form.onFinish)\n" +
            "  · syncRaidCard·Fresh·CategoryAware 예외 stack trace 로그 (silent fail 방지)";
}
