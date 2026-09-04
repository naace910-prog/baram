package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.53";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🚑 진짜 원인: 부팅 시 JDA 로그인 실패 = 봇 영구 사망 (재시도 없었음)\n" +
            "  · JDA Status=null / API 호출 0건 → rate limit 이 아니라 봇 자체가 없던 것\n" +
            "  · connectInBackground 1회 실패 시 catch 만 하고 끝 → 재배포 전까지 복구 불가\n" +
            "🔁 재시도 루프 도입: 60s→120s→300s→600s→900s→1800s backoff 무한 재시도\n" +
            "  · 실패 인스턴스 shutdownNow 로 정리 (IDENTIFY 낭비 방지)\n" +
            "🩺 진단에 lastConnectError·시도횟수·루프상태 노출 + [봇 재연결] 버튼";
}
