package com.wind.guild.repository;

import com.wind.guild.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByOrderByIdDesc(Pageable pageable);
    List<ChatMessage> findByIdGreaterThanOrderByIdAsc(Long since);
    boolean existsByDiscordMessageId(Long discordMessageId);
}
