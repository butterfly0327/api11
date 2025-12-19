package com.yumyumcoach.domain.ai.chat.controller;

import com.yumyumcoach.domain.ai.chat.dto.AiChatRequest;
import com.yumyumcoach.domain.ai.chat.dto.AiChatResponse;
import com.yumyumcoach.domain.ai.chat.service.AiChatService;
import com.yumyumcoach.global.common.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/ai-chat")
public class AiChatController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final AiChatService aiChatService;

    @PostMapping("/send")
    public AiChatResponse sendMessage(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid AiChatRequest request
    ) {
        String email = CurrentUser.email();
        LocalDate targetDate = date != null ? date : LocalDate.now(KST);
        return aiChatService.chat(email, targetDate, request);
    }

    @GetMapping("/history")
    public AiChatResponse getHistory(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        String email = CurrentUser.email();
        LocalDate targetDate = date != null ? date : LocalDate.now(KST);
        return aiChatService.getMessages(email, targetDate);
    }
}
