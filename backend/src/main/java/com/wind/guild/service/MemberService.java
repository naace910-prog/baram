package com.wind.guild.service;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.repository.MemberRepository;
import com.wind.guild.web.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<MemberDto.View> listActive() {
        return memberRepository.findAllByActiveTrueOrderByNicknameAsc()
                .stream().map(MemberDto.View::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MemberDto.View> listAll() {
        return memberRepository.findAll().stream().map(MemberDto.View::of).toList();
    }

    public MemberDto.View create(MemberDto.CreateRequest req) {
        if (memberRepository.existsByAccount(req.account())) {
            throw new IllegalArgumentException("이미 존재하는 계정입니다: " + req.account());
        }
        Member saved = memberRepository.save(Member.builder()
                .account(req.account())
                .password(passwordEncoder.encode(req.password()))
                .nickname(req.nickname())
                .role(req.role())
                .discordUserId(req.discordUserId())
                .active(true)
                .build());
        return MemberDto.View.of(saved);
    }

    public MemberDto.View update(Long id, MemberDto.UpdateRequest req) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + id));
        m.setNickname(req.nickname());
        m.setRole(req.role());
        m.setDiscordUserId(req.discordUserId());
        if (req.active() != null) m.setActive(req.active());
        return MemberDto.View.of(m);
    }

    public void resetPassword(Long id, String newPassword) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + id));
        m.setPassword(passwordEncoder.encode(newPassword));
    }

    public void changePassword(Long id, String currentPassword, String newPassword) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + id));
        if (!passwordEncoder.matches(currentPassword, m.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다");
        }
        m.setPassword(passwordEncoder.encode(newPassword));
    }

    public String changeNickname(Long id, String newNickname) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + id));
        String trimmed = newNickname == null ? "" : newNickname.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("닉네임을 입력해주세요");
        if (trimmed.length() > 40) throw new IllegalArgumentException("닉네임은 40자 이내로 입력해주세요");
        m.setNickname(trimmed);
        return trimmed;
    }

    public MemberDto.View setStarred(Long id, boolean starred) {
        Member m = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("문파원 없음: " + id));
        if (starred && m.getRole() != MemberRole.MASTER) {
            throw new IllegalStateException("문주 권한을 가진 문파원만 중요 표시할 수 있습니다");
        }
        m.setStarred(starred);
        return MemberDto.View.of(m);
    }
}
