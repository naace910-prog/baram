package com.wind.guild.config;

public final class AppVersion {
    private AppVersion() {}

    public static final String VERSION = "v1.0.54";

    /**
     * 이번 배포 변경사항만 담을 것 (누적 X · Discord 메시지 2000자 제한).
     * 새 배포마다 이 값을 갈아엎기.
     */
    public static final String CHANGELOG =
            "🔍 [IP 차단 검사] 추가 — 봇 로그인·사이트 OAuth 로그인 둘 다 429 인 이유 판별\n" +
            "  · 인증 불필요 엔드포인트(/api/v10/gateway) 호출 → 429 면 IP 레벨 차단 확정\n" +
            "  · 봇토큰·OAuth 자격증명과 무관하게 Render 아웃바운드 IP 가 막힌 상태라는 뜻\n" +
            "🔐 OAuth 토큰 교환 429 를 사용자에게 명확한 문구로 안내 (기존: 일반 실패로 뭉개짐)\n" +
            "📋 discord_api_logs 기록 실패를 조용히 삼키지 않고 WARN 로그\n" +
            "  · '0건' 이 호출 없음인지 로깅 고장인지 구분 가능";
}
