package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.32";

    public static final String CHANGELOG =
            "• **분배자 표기** — 누가 언제 분배했는지 카드/사이트에 표시 (raid_loots.distributed_by/at 컬럼)\n" +
            "• **레이드 완료 후 24시간 미분배 알림** — DONE 후 1일 지나도 판매금 미입력 or 미분배인 loot 있으면 Discord 알림 1회 발송 (스팸 방지 flag)\n" +
            "• **옵션 2 정책** — 카테고리별 최초 발송만 new, 이후는 기존 카드 편집:\n" +
            "  · PARTY (파티 저장/자동배정): 첫 저장은 new, 이후 저장은 edit\n" +
            "  · LOOT (득템 등록/판매금): 첫 등록은 new, 이후는 edit\n" +
            "  · DIST (분배): 첫 분배는 new, 이후는 edit\n" +
            "  · 지급(paid) 토글: 항상 edit\n" +
            "  · syncRaidCardCategoryAware 로 통일\n" +
            "• 사이트 주요 모달 (분배 · 득템편집 · 드랍대량입력) Enter 키 → 확인 버튼 자동 클릭\n" +
            "\n" +
            "🚀 **성능 최적화**\n" +
            "• Discord 카드 embed rebuild N+1 쿼리 제거 — 30+ 쿼리 → 6~8 쿼리 (~5배 빠름)\n" +
            "  · RaidCardData 캐시 홀더 도입, syncRaidCard 마다 1회 로드\n" +
            "  · LootShareRepository.findByLootIdIn / RaidPartyMemberRepository.findByPartyIdIn 배치\n" +
            "• Discord/WebPush 발송 = @Async('discordExecutor') 백그라운드 스레드\n" +
            "  · 백엔드 API 응답이 외부 I/O 를 안 기다림 (레이드 저장/투표/지급 클릭 즉시 응답)\n" +
            "  · ThreadPoolTaskExecutor (core 2, max 4, queue 100)\n" +
            "• Discord 로그인 **매번 승인 화면 뜨는 것 해결** — `prompt=none` 으로 이미 승인한 앱이면 즉시 자동 로그인 (최초 1회만 승인 필요)\n" +
            "  · 최초 사용자 or 승인 만료 시 (consent_required 에러) 자동으로 승인 화면 fallback";
}
