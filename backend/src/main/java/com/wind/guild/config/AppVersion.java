package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.55";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "⚠️ IP 레벨 차단 확정 (인증 없는 엔드포인트조차 429)\n" +
            "  · 봇토큰·OAuth·앱 코드 전부 무관 · Render 아웃바운드 IP 가 Discord 에 차단됨\n" +
            "🐢 429 전용 재시도 backoff 분리: 900s → 1800s → 3600s\n" +
            "  · 종전 60s 부터 재시도 = 차단 상태에서 계속 두드려 차단 연장시키는 행위\n" +
            "  · 이제 차단 감지 시 최소 15분 쉬고 재시도 → 해제되면 자동 복구";
}
