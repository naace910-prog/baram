package com.wind.guild.config;

import com.wind.guild.domain.Member;
import com.wind.guild.domain.MemberRole;
import com.wind.guild.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (memberRepository.count() == 0) {
            Member master = Member.builder()
                    .account("master")
                    .password(passwordEncoder.encode("1234"))
                    .nickname("문주")
                    .role(MemberRole.MASTER)
                    .active(true)
                    .build();
            memberRepository.save(master);
            log.info("초기 문주 계정 생성: master / 1234  (로그인 후 즉시 변경 권장)");
        }
    }
}
