package com.yumyumcoach.domain.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessageDto {
    private String sender;
    private String content;
    private LocalDateTime createdAt;
}
