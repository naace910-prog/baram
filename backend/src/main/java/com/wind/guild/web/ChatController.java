package com.wind.guild.web;

import com.wind.guild.config.SessionKeys;
import com.wind.guild.service.ChatService;
import com.wind.guild.web.dto.ChatDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/messages")
    public List<ChatDto.MessageView> history(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) Long since) {
        if (since != null) return chatService.since(since);
        return chatService.recent(limit);
    }

    @PostMapping("/messages")
    public ChatDto.MessageView send(@Valid @RequestBody ChatDto.SendRequest req, HttpSession session) {
        Long memberId = (Long) session.getAttribute(SessionKeys.MEMBER_ID);
        if (memberId == null) throw new IllegalStateException("로그인이 필요합니다");
        String nickname = (String) session.getAttribute(SessionKeys.MEMBER_NICKNAME);
        return chatService.saveFromSite(req.content(), memberId, nickname != null ? nickname : "?");
    }
}
