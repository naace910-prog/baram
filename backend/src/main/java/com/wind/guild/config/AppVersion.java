package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.29";

    public static final String CHANGELOG =
            "• **분배자 표기** — 누가 언제 분배했는지 카드/사이트에 표시 (raid_loots.distributed_by/at 컬럼)\n" +
            "• **레이드 완료 후 24시간 미분배 알림** — DONE 후 1일 지나도 판매금 미입력 or 미분배인 loot 있으면 Discord 알림 1회 발송 (스팸 방지 flag)\n" +
            "• **옵션 2 정책** — 카테고리별 최초 발송만 new, 이후는 기존 카드 편집:\n" +
            "  · PARTY (파티 저장/자동배정): 첫 저장은 new, 이후 저장은 edit\n" +
            "  · LOOT (득템 등록/판매금): 첫 등록은 new, 이후는 edit\n" +
            "  · DIST (분배): 첫 분배는 new, 이후는 edit\n" +
            "  · 지급(paid) 토글: 항상 edit\n" +
            "  · syncRaidCardCategoryAware 로 통일";
}
