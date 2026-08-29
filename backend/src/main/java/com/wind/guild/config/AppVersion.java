package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.48";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🐛 파티 편성 중복 인원 저장 실패 fix\n" +
            "  · autoYesVoteForPartyMembers: 같은 memberId 가 두 슬롯에 있고 아직 vote 없으면\n" +
            "    2번째 loop 에서 새 RaidVote 재삽입 → unique(raid_id, member_id) 위반 500\n" +
            "  · processed Set 로 dedup + save 후 map 에 반영";
}
