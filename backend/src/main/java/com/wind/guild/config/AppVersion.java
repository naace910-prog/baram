package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.56";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🎯 완료 카드(득템 입력 버튼)가 안 보이던 문제 fix\n" +
            "  · STATUS 트리거는 forceNew 조건에 없어 기존 메시지를 edit 만 했음\n" +
            "  · 그 기존 메시지는 대개 30분 전 리마인더 → 채팅 위로 묻힘\n" +
            "  · Discord 는 edit 시 알림도 없고 위치도 안 바뀌어 사용자가 못 봄\n" +
            "  · doneFreshSent 플래그 추가 → DONE 최초 1회는 새 메시지로 발송\n" +
            "  · 자동완료·수동완료 두 경로 모두 CategoryAware 로 전환";
}
