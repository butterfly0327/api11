package com.yumyumcoach.domain.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private LocalDate messageDate;
    private List<AiChatMessageDto> messages;
}
