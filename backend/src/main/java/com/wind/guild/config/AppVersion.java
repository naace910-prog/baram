package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.45";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🐛 최초등록·재발송 카드 미발송 근본원인 fix\n" +
            "  · 자동 생성 본대 파티(멤버 0명) → partyMembersMap.get(pid)=null\n" +
            "  · appendPartiesToEmbedShared·collectPartyParticipants 에서 NPE\n" +
            "  · outer try/catch 가 예외 삼켜 조용히 실패했음 (리마인더는 멤버 편성 후라 정상)\n" +
            "  · getOrDefault(pid, List.of()) 로 null 안전";
}
