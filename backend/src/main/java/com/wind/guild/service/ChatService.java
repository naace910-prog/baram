package com.wind.guild.service;

import com.wind.guild.domain.ChatMessage;
import com.wind.guild.domain.ChatOrigin;
import com.wind.guild.repository.ChatMessageRepository;
import com.wind.guild.web.dto.ChatDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository repo;
    private final ChatBroadcaster broadcaster;
    private final ChatDiscordBridge bridge;

    @Transactional(readOnly = true)
    public List<ChatDto.MessageView> recent(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<ChatMessage> desc = repo.findAllByOrderByIdDesc(PageRequest.of(0, cap));
        List<ChatDto.MessageView> asc = desc.stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(ChatDto.MessageView::of)
                .toList();
        return asc;
    }

    @Transactional(readOnly = true)
    public List<ChatDto.MessageView> since(Long sinceId) {
        return repo.findByIdGreaterThanOrderByIdAsc(sinceId).stream()
                .map(ChatDto.MessageView::of).toList();
    }

    public ChatDto.MessageView saveFromSite(String content, Long memberId, String nickname) {
        ChatMessage m = repo.save(ChatMessage.builder()
                .content(content.trim())
                .authorMemberId(memberId)
                .authorNickname(nickname)
                .origin(ChatOrigin.SITE)
                .build());
        ChatDto.MessageView view = ChatDto.MessageView.of(m);
        broadcaster.broadcast(view);
        bridge.relayToDiscord(view);
        return view;
    }

    public ChatDto.MessageView saveFromDiscord(String content, String discordUserId, String nickname, Long discordMessageId) {
        if (discordMessageId != null && repo.existsByDiscordMessageId(discordMessageId)) {
            return null;
        }
        ChatMessage m = repo.save(ChatMessage.builder()
                .content(content == null ? "" : (content.length() > 2000 ? content.substring(0, 2000) : content))
                .authorDiscordId(discordUserId)
                .authorNickname(nickname == null ? "Discord유저" : nickname)
                .origin(ChatOrigin.DISCORD)
                .discordMessageId(discordMessageId)
                .build());
        ChatDto.MessageView view = ChatDto.MessageView.of(m);
        broadcaster.broadcast(view);
        return view;
    }
}
