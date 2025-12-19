package com.yumyumcoach.domain.ai.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {
    private Long id;
    private String email;
    private LocalDate messageDate;
    private String sender;
    private String content;
    private LocalDateTime createdAt;
}
