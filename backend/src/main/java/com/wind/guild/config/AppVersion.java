package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.35";

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
            "  · 최초 사용자 or 승인 만료 시 (consent_required 에러) 자동으로 승인 화면 fallback\n" +
            "🔧 hotfix: Discord 투표 버튼 3초 지연 — updateEmbed/notifyRaidPre30 이 self-invoke 로 @Async 우회되던 문제 (@Async 어노테이션 자체 메서드에 붙임)\n" +
            "• 로그인 자동 시도: 세션마다 1회 Discord prompt=none 로 silent 로그인 시도\n" +
            "  · 성공 시 즉시 홈, 실패 시 로그인 화면 유지 (에러 알림 표시)\n" +
            "  · 재로그인 클릭 수 감소\n" +
            "• **전역 로딩 인디케이터** — 모든 API 호출 시 상단에 얇은 progress bar (axios interceptor 기반)\n" +
            "🔧 hotfix: 30분 리마인더 반복 발송 버그 — race condition (async Discord callback 이 stale raid entity 를 save 하며 pre30Sent=false 로 덮어씀). discordMessageId 만 부분 update 하는 targeted query 로 교체\n" +
            "• 파티 편성 사이드바: YES 투표자 = 초록색 태그 (한눈에 구분)\n" +
            "• 파티에 문파원 추가 시 자동 YES 투표 (기존 vote 상관없이)";
}
