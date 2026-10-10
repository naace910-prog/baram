package com.wind.guild.web;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.service.AuthService;
import com.wind.guild.service.DesktopLoginService;
import com.wind.guild.service.DiscordOAuthService;
import com.wind.guild.service.MemberService;
import com.wind.guild.web.dto.AuthDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;
    private final DiscordOAuthService discordOAuth;
    private final DiscordProperties discordProps;
    private final DesktopLoginService desktopLogin;

    @PostMapping("/login")
    public AuthDto.LoginResponse login(@Valid @RequestBody AuthDto.LoginRequest req, HttpSession session) {
        return authService.login(req, session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        authService.logout(session);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.LoginResponse> me(HttpSession session) {
        Long id = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (id == null) return ResponseEntity.status(401).build();
        String role = (String) session.getAttribute(SessionKeys.MEMBER_ROLE);
        return ResponseEntity.ok(new AuthDto.LoginResponse(
                id,
                (String) session.getAttribute(SessionKeys.MEMBER_ACCOUNT),
                (String) session.getAttribute(SessionKeys.MEMBER_NICKNAME),
                MemberRole.valueOf(role)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody AuthDto.ChangePasswordRequest req,
            HttpSession session) {
        Long id = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (id == null) return ResponseEntity.status(401).build();
        memberService.changePassword(id, req.currentPassword(), req.newPassword());
        return ResponseEntity.ok(Map.of("result", "ok"));
    }

    @PostMapping("/change-nickname")
    public ResponseEntity<Map<String, String>> changeNickname(
            @Valid @RequestBody AuthDto.ChangeNicknameRequest req,
            HttpSession session) {
        Long id = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (id == null) return ResponseEntity.status(401).build();
        String updated = memberService.changeNickname(id, req.nickname());
        // 세션 닉네임도 즉시 갱신
        session.setAttribute(SessionKeys.MEMBER_NICKNAME, updated);
        return ResponseEntity.ok(Map.of("result", "ok", "nickname", updated));
    }

    @GetMapping("/discord/authorize-url")
    public Map<String, Object> discordAuthorizeUrl() {
        if (!discordOAuth.isConfigured()) {
            return Map.of("enabled", false);
        }
        return Map.of("enabled", true, "url", discordOAuth.buildAuthorizeUrl());
    }

    // ===== 데스크톱 앱(쿨타임 도우미) Discord 로그인 =====
    // 앱: verifier 생성 → 브라우저로 /desktop/begin?challenge=sha256(verifier) → Discord 승인
    //     → 콜백이 challenge 에 회원 기록 → 앱이 /desktop/poll 에 verifier 를 보내 자기 세션을 받는다

    @GetMapping("/desktop/begin")
    public void desktopBegin(@RequestParam(required = false) String challenge,
                             HttpServletResponse resp) throws java.io.IOException {
        if (!discordOAuth.isConfigured()) {
            desktopPage(resp, 503, "Discord 로그인이 꺼져 있습니다", "문주에게 문의해 주세요.");
            return;
        }
        if (!desktopLogin.begin(challenge)) {
            desktopPage(resp, 400, "잘못된 로그인 요청입니다", "앱에서 다시 'Discord로 로그인'을 눌러 주세요.");
            return;
        }
        resp.sendRedirect(discordOAuth.buildAuthorizeUrl(DESKTOP_STATE + challenge));
    }

    @PostMapping("/desktop/poll")
    public ResponseEntity<Map<String, Object>> desktopPoll(@RequestBody Map<String, String> body,
                                                           HttpSession session) {
        var p = desktopLogin.take(body == null ? null : body.get("verifier"));
        if (p == null) return ResponseEntity.status(404).body(Map.of("status", "expired"));
        if (!p.done()) return ResponseEntity.ok(Map.of("status", "pending"));
        if (p.error() != null) return ResponseEntity.ok(Map.of("status", "error", "reason", p.error()));
        try {
            return ResponseEntity.ok(Map.of("status", "ok", "member", authService.loginById(p.memberId(), session)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("status", "error", "reason", "INACTIVE"));
        }
    }

    private static final String DESKTOP_STATE = "desktop.";

    private void desktopPage(HttpServletResponse resp, int status, String title, String detail)
            throws java.io.IOException {
        resp.setStatus(status);
        resp.setContentType("text/html; charset=UTF-8");
        resp.getWriter().write("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>바람 쿨타임 도우미</title></head>"
                + "<body style=\"font-family:'Malgun Gothic',sans-serif;background:#16181d;color:#e8eaee;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0\">"
                + "<div style=\"text-align:center\"><div style=\"font-size:40px\">🌸</div>"
                + "<h2>" + org.springframework.web.util.HtmlUtils.htmlEscape(title) + "</h2>"
                + "<p style=\"color:#9aa1ad\">" + org.springframework.web.util.HtmlUtils.htmlEscape(detail) + "</p>"
                + "</div></body></html>");
    }

    private static String desktopReasonText(String reason) {
        return switch (reason == null ? "" : reason) {
            case "NOT_GUILD_MEMBER" -> "문파 Discord 서버에 들어와 있는 계정만 쓸 수 있습니다.";
            case "INACTIVE" -> "비활성 계정입니다. 문주에게 문의해 주세요.";
            case "GUILD_NOT_CONFIGURED" -> "서버 설정이 끝나지 않았습니다. 문주에게 문의해 주세요.";
            case "access_denied" -> "Discord 승인을 취소했습니다.";
            default -> "다시 시도해 주세요. (" + reason + ")";
        };
    }

    @GetMapping("/discord/callback")
    public void discordCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            HttpSession session,
            HttpServletResponse resp) throws java.io.IOException {
        String challenge = state != null && state.startsWith(DESKTOP_STATE)
                ? state.substring(DESKTOP_STATE.length()) : null;
        if (challenge != null && desktopLogin.isWaiting(challenge)) {
            desktopCallback(code, error, challenge, session, resp);
            return;
        }
        String base = discordProps.getOauthLoginSuccessRedirect();
        if (error != null || code == null) {
            // prompt=none 인데 최초 사용자 or Discord 세션 만료 → 승인 화면으로 재시도
            if ("consent_required".equals(error) || "interaction_required".equals(error) || "login_required".equals(error)) {
                // prompt 없이 재시도 (기본 = consent)
                String fallback = discordOAuth.buildAuthorizeUrl().replace("&prompt=none", "");
                resp.sendRedirect(fallback);
                return;
            }
            resp.sendRedirect(base + "login?discordError=" + (error == null ? "no_code" : error));
            return;
        }
        try {
            var result = discordOAuth.handleCallback(code, session);
            if (result.isLoggedIn()) {
                resp.sendRedirect(base);
            } else {
                String reason = result.getReason();
                String uid = result.getUser().id();
                String name = result.getUser().globalName() != null
                        ? result.getUser().globalName() : result.getUser().username();
                resp.sendRedirect(base + "login?discordError=" + reason
                        + "&discordId=" + URLEncoder.encode(uid, StandardCharsets.UTF_8)
                        + "&discordName=" + URLEncoder.encode(name == null ? "" : name, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.length() > 200) msg = msg.substring(0, 200);
            resp.sendRedirect(base + "login?discordError=exchange_failed"
                    + "&detail=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
        }
    }

    private void desktopCallback(String code, String error, String challenge,
                                 HttpSession session, HttpServletResponse resp) throws java.io.IOException {
        String state = DESKTOP_STATE + challenge;
        if (error != null || code == null) {
            if ("consent_required".equals(error) || "interaction_required".equals(error) || "login_required".equals(error)) {
                // 웹 로그인과 같은 재시도 — state 를 유지해야 앱 로그인으로 돌아온다
                resp.sendRedirect(discordOAuth.buildAuthorizeUrl(state).replace("&prompt=none", ""));
                return;
            }
            String reason = error == null ? "no_code" : error;
            desktopLogin.fail(challenge, reason);
            desktopPage(resp, 200, "로그인하지 못했습니다", desktopReasonText(reason));
            return;
        }
        try {
            var result = discordOAuth.handleCallback(code, session);
            if (result.isLoggedIn()) {
                desktopLogin.complete(challenge, (Long) session.getAttribute(SessionKeys.MEMBER_ID));
                desktopPage(resp, 200, "로그인 완료", "이 창을 닫고 쿨타임 도우미로 돌아가세요.");
            } else {
                desktopLogin.fail(challenge, result.getReason());
                desktopPage(resp, 200, "로그인하지 못했습니다", desktopReasonText(result.getReason()));
            }
        } catch (Exception e) {
            desktopLogin.fail(challenge, "exchange_failed");
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            desktopPage(resp, 200, "로그인하지 못했습니다", msg.length() > 200 ? msg.substring(0, 200) : msg);
        }
    }
}
