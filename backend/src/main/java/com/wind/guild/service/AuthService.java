package com.wind.guild.service;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.domain.Member;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.web.dto.AuthDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthDto.LoginResponse login(AuthDto.LoginRequest req, HttpSession session) {
        Member m = memberRepository.findByAccount(req.account())
                .orElseThrow(() -> new IllegalArgumentException("계정 또는 비밀번호가 올바르지 않습니다"));
        if (!m.isActive()) {
            throw new IllegalArgumentException("비활성 계정입니다");
        }
        if (!passwordEncoder.matches(req.password(), m.getPassword())) {
            throw new IllegalArgumentException("계정 또는 비밀번호가 올바르지 않습니다");
        }
        session.setAttribute(SessionKeys.MEMBER_ID, m.getId());
        session.setAttribute(SessionKeys.MEMBER_ACCOUNT, m.getAccount());
        session.setAttribute(SessionKeys.MEMBER_NICKNAME, m.getNickname());
        session.setAttribute(SessionKeys.MEMBER_ROLE, m.getRole().name());
        return new AuthDto.LoginResponse(m.getId(), m.getAccount(), m.getNickname(), m.getRole());
    }

    /** 데스크톱 앱 Discord 로그인 — 브라우저에서 승인된 회원으로 앱 쪽 세션을 만든다 */
    public AuthDto.LoginResponse loginById(Long memberId, HttpSession session) {
        Member m = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다"));
        if (!m.isActive()) {
            throw new IllegalArgumentException("비활성 계정입니다");
        }
        session.setAttribute(SessionKeys.MEMBER_ID, m.getId());
        session.setAttribute(SessionKeys.MEMBER_ACCOUNT, m.getAccount());
        session.setAttribute(SessionKeys.MEMBER_NICKNAME, m.getNickname());
        session.setAttribute(SessionKeys.MEMBER_ROLE, m.getRole().name());
        return new AuthDto.LoginResponse(m.getId(), m.getAccount(), m.getNickname(), m.getRole());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}
