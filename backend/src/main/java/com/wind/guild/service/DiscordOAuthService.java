package com.wind.guild.service;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Member;
import com.wind.guild.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordOAuthService {

    private final DiscordProperties props;
    private final MemberRepository memberRepository;
    private final RestTemplate rest = new RestTemplate();

    public boolean isConfigured() {
        return props.getClientId() != null && !props.getClientId().isBlank()
                && props.getClientSecret() != null && !props.getClientSecret().isBlank();
    }

    public String buildAuthorizeUrl() {
        if (!isConfigured()) throw new IllegalStateException("Discord OAuth이 설정되지 않았습니다");
        return "https://discord.com/api/oauth2/authorize"
                + "?client_id=" + props.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(props.getOauthRedirectUri(), StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=identify";
    }

    public DiscordCallbackResult handleCallback(String code, HttpSession session) {
        DiscordUser user = fetchUser(code);
        Optional<Member> found = memberRepository.findByDiscordUserId(user.id());
        if (found.isPresent()) {
            Member m = found.get();
            if (!m.isActive()) return new DiscordCallbackResult(user, false, "INACTIVE");
            session.setAttribute(SessionKeys.MEMBER_ID, m.getId());
            session.setAttribute(SessionKeys.MEMBER_ACCOUNT, m.getAccount());
            session.setAttribute(SessionKeys.MEMBER_NICKNAME, m.getNickname());
            session.setAttribute(SessionKeys.MEMBER_ROLE, m.getRole().name());
            return new DiscordCallbackResult(user, true, null);
        }
        return new DiscordCallbackResult(user, false, "NOT_REGISTERED");
    }

    private DiscordUser fetchUser(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getOauthRedirectUri());
        HttpHeaders th = new HttpHeaders();
        th.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> tokenReq = new HttpEntity<>(form, th);
        @SuppressWarnings("unchecked")
        Map<String, Object> token = rest.postForObject(
                "https://discord.com/api/oauth2/token", tokenReq, Map.class);
        if (token == null || !token.containsKey("access_token")) {
            throw new IllegalStateException("Discord 토큰 발급 실패");
        }
        String at = (String) token.get("access_token");
        HttpHeaders uh = new HttpHeaders();
        uh.setBearerAuth(at);
        HttpEntity<Void> userReq = new HttpEntity<>(uh);
        ResponseEntity<Map> resp = rest.exchange(
                "https://discord.com/api/users/@me", HttpMethod.GET, userReq, Map.class);
        Map<?, ?> body = resp.getBody();
        if (body == null) throw new IllegalStateException("Discord 유저 조회 실패");
        return new DiscordUser(
                String.valueOf(body.get("id")),
                (String) body.get("username"),
                (String) body.get("global_name"));
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
