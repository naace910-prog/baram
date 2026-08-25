package com.wind.guild.web;

import com.wind.guild.config.DiscordProperties;
import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.service.AuthService;
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

    @GetMapping("/discord/authorize-url")
    public Map<String, Object> discordAuthorizeUrl() {
        if (!discordOAuth.isConfigured()) {
            return Map.of("enabled", false);
        }
        return Map.of("enabled", true, "url", discordOAuth.buildAuthorizeUrl());
    }

    @GetMapping("/discord/callback")
    public void discordCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpSession session,
            HttpServletResponse resp) throws java.io.IOException {
        String base = discordProps.getOauthLoginSuccessRedirect();
        if (error != null || code == null) {
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
}
