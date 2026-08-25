package com.wind.guild.web;

import com.wind.guild.service.MemberService;
import com.wind.guild.web.dto.MemberDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public List<MemberDto.View> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return includeInactive ? memberService.listAll() : memberService.listActive();
    }

    @PostMapping
    public MemberDto.View create(@Valid @RequestBody MemberDto.CreateRequest req) {
        return memberService.create(req);
    }

    @PutMapping("/{id}")
    public MemberDto.View update(@PathVariable Long id, @Valid @RequestBody MemberDto.UpdateRequest req) {
        return memberService.update(id, req);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        memberService.resetPassword(id, body.getOrDefault("newPassword", "1234"));
        return Map.of("result", "ok");
    }

    @PostMapping("/{id}/starred")
    public MemberDto.View setStarred(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return memberService.setStarred(id, Boolean.TRUE.equals(body.get("starred")));
    }
}
