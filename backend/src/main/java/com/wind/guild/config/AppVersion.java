package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.51";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🛡 Discord 429 감지 시 전역 10분 cooldown (재쇄도로 ban 연장 방지)\n" +
            "  · 어느 send/edit 콜백이든 err.toString 에 '429' 또는 'rate limit' 발견 시\n" +
            "    globalCooldownUntilMillis 를 now+10분 으로 설정\n" +
            "  · syncRaidCard·Fresh·postAlertMessage·postRaidPre30Fresh·syncLootCard 진입부에서\n" +
            "    cooldown 체크 → 남은 초 로그 후 즉시 return (Discord API 호출 안 함)\n" +
            "  · Discord 자체 ban 풀리는 시간과 별개로 우리도 API 자제 → ban 연장 위험 최소화";
}
