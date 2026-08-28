package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.42";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🔧 hotfix: 수동 완료 시 자동 다음 raid Discord 카드 미발송\n" +
            "  · RaidService.update 에서 createNextAfterDone 은 되나 syncRaidCard 를 안 불렀음\n" +
            "  · UpdateResult record 반환 → 컨트롤러가 next raid syncRaidCard(CREATED) + 채팅 알림\n" +
            "  · 스케줄러 자동완료 경로와 동일 처리";
}
