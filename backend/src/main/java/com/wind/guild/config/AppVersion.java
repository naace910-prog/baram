package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.50";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🚨 Discord 글로벌 429 대응 (v1.0.49 TZ fix 부작용)\n" +
            "  · TZ 수정 후 누적 stale raid 가 한 tick 에 몰려 처리 → API 폭주 → 글로벌 rate limit\n" +
            "  · autoComplete: tick 당 최대 3개만 처리 (나머지 다음 tick)\n" +
            "  · 6시간 이전 raid 는 backfill 로 간주 → 조용히 DONE, Discord 카드·챗·next 생성 모두 스킵\n" +
            "  · 정상 raid (30분~6시간) 만 카드 발송";
}
