package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordOAuthService {

    private final DiscordProperties props;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate rest = new RestTemplate();

    public boolean isConfigured() {
        return props.isEnabled()
                && props.getClientId() != null && !props.getClientId().isBlank()
                && props.getClientSecret() != null && !props.getClientSecret().isBlank();
    }

    public String buildAuthorizeUrl() {
        if (!isConfigured()) throw new IllegalStateException("Discord OAuth이 설정되지 않았습니다");
        return "https://discord.com/api/oauth2/authorize"
                + "?client_id=" + props.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(props.getOauthRedirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode("identify guilds", StandardCharsets.UTF_8);
    }

    @Transactional
    public DiscordCallbackResult handleCallback(String code, HttpSession session) {
        String accessToken = exchangeCodeForToken(code);
        DiscordUser user = fetchUser(accessToken);

        Optional<Member> found = memberRepository.findByDiscordUserId(user.id());
        if (found.isPresent()) {
            Member m = found.get();
            if (!m.isActive()) return new DiscordCallbackResult(user, false, "INACTIVE");
            setSession(session, m);
            return new DiscordCallbackResult(user, true, null);
        }

        if (props.getGuildId() == null || props.getGuildId().isBlank()) {
            return new DiscordCallbackResult(user, false, "GUILD_NOT_CONFIGURED");
        }
        boolean isGuildMember = fetchUserGuildIds(accessToken).contains(props.getGuildId());
        if (!isGuildMember) {
            return new DiscordCallbackResult(user, false, "NOT_GUILD_MEMBER");
        }

        Member created = autoRegister(user);
        setSession(session, created);
        log.info("자동 문파원 등록: {} (Discord ID {})", created.getNickname(), user.id());
        return new DiscordCallbackResult(user, true, null);
    }

    private Member autoRegister(DiscordUser user) {
        String nickname = (user.globalName() != null && !user.globalName().isBlank())
                ? user.globalName() : user.username();
        if (nickname == null || nickname.isBlank()) nickname = "user_" + user.id();
        return memberRepository.save(Member.builder()
                .account("discord_" + user.id())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .nickname(nickname)
                .role(MemberRole.MEMBER)
                .discordUserId(user.id())
                .active(true)
                .build());
    }

    private void setSession(HttpSession session, Member m) {
        session.setAttribute(SessionKeys.MEMBER_ID, m.getId());
        session.setAttribute(SessionKeys.MEMBER_ACCOUNT, m.getAccount());
        session.setAttribute(SessionKeys.MEMBER_NICKNAME, m.getNickname());
        session.setAttribute(SessionKeys.MEMBER_ROLE, m.getRole().name());
    }

    private String exchangeCodeForToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getOauthRedirectUri());
        HttpHeaders th = new HttpHeaders();
        th.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, th);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = rest.postForObject("https://discord.com/api/oauth2/token", req, Map.class);
        if (body == null || !body.containsKey("access_token")) {
            throw new IllegalStateException("Discord 토큰 발급 실패");
        }
        return (String) body.get("access_token");
    }

    private DiscordUser fetchUser(String accessToken) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        ResponseEntity<Map> resp = rest.exchange(
                "https://discord.com/api/users/@me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        Map<?, ?> body = resp.getBody();
        if (body == null) throw new IllegalStateException("Discord 유저 조회 실패");
        return new DiscordUser(
                String.valueOf(body.get("id")),
                (String) body.get("username"),
                (String) body.get("global_name"));
    }

    @SuppressWarnings("unchecked")
    private List<String> fetchUserGuildIds(String accessToken) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(accessToken);
        ResponseEntity<List> resp = rest.exchange(
                "https://discord.com/api/users/@me/guilds", HttpMethod.GET, new HttpEntity<>(h), List.class);
        List<?> body = resp.getBody();
        if (body == null) return List.of();
        return body.stream()
                .filter(o -> o instanceof Map)
                .map(o -> (Map<?, ?>) o)
                .map(m -> String.valueOf(m.get("id")))
                .toList();
    }

    public record DiscordUser(String id, String username, String globalName) {}

    @Getter
    public static class DiscordCallbackResult {
        private final DiscordUser user;
        private final boolean loggedIn;
        private final String reason;
        public DiscordCallbackResult(DiscordUser user, boolean loggedIn, String reason) {
            this.user = user; this.loggedIn = loggedIn; this.reason = reason;
        }
    }
}
