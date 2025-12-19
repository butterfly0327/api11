package com.yumyumcoach.domain.ai.chat.service;

import com.yumyumcoach.domain.ai.chat.dto.AiChatMessageDto;
import com.yumyumcoach.domain.ai.chat.dto.AiChatRequest;
import com.yumyumcoach.domain.ai.chat.dto.AiChatResponse;
import com.yumyumcoach.domain.ai.chat.entity.AiChatMessage;
import com.yumyumcoach.domain.ai.chat.mapper.AiChatMessageMapper;
import com.yumyumcoach.domain.ai.service.GeminiClient;
import com.yumyumcoach.domain.stats.dto.WeeklyStatsResponse;
import com.yumyumcoach.domain.stats.service.WeeklyStatsService;
import com.yumyumcoach.domain.user.dto.MyPageResponse;
import com.yumyumcoach.domain.user.service.UserService;
import com.yumyumcoach.global.exception.BusinessException;
import com.yumyumcoach.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiChatService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AiChatMessageMapper aiChatMessageMapper;
    private final GeminiClient geminiClient;
    private final WeeklyStatsService weeklyStatsService;
    private final UserService userService;

    @Transactional
    public AiChatResponse chat(String email, LocalDate date, AiChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "대화 내용을 입력해주세요.");
        }

        MyPageResponse profile = userService.getMyPage(email);
        WeeklyStatsResponse weeklyStats = weeklyStatsService.getWeeklyStats(email, date);

        String prompt = buildPrompt(profile, weeklyStats, date, request.getMessage());
        String answer = geminiClient.generate(prompt);

        List<AiChatMessage> messages = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(KST);
        messages.add(AiChatMessage.builder()
                .email(email)
                .messageDate(date)
                .sender("USER")
                .content(request.getMessage())
                .createdAt(now)
                .build());
        messages.add(AiChatMessage.builder()
                .email(email)
                .messageDate(date)
                .sender("AI")
                .content(answer)
                .createdAt(now.plusSeconds(1))
                .build());
        aiChatMessageMapper.insertMessages(messages);

        return getMessages(email, date);
    }

    @Transactional(readOnly = true)
    public AiChatResponse getMessages(String email, LocalDate date) {
        List<AiChatMessage> rows = aiChatMessageMapper.findByEmailAndDate(email, date);
        List<AiChatMessageDto> dtoList = rows.stream()
                .sorted(Comparator.comparing(AiChatMessage::getCreatedAt))
                .map(row -> AiChatMessageDto.builder()
                        .sender(row.getSender())
                        .content(row.getContent())
                        .createdAt(row.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AiChatResponse.builder()
                .messageDate(date)
                .messages(dtoList)
                .build();
    }

    private String buildPrompt(MyPageResponse profile, WeeklyStatsResponse weeklyStats, LocalDate date, String message) {
        LocalDateTime now = LocalDateTime.now(KST);
        StringBuilder sb = new StringBuilder();
        sb.append("오늘 날짜는 ").append(date).append("이며 요일은 ").append(date.getDayOfWeek()).append("입니다. ");
        sb.append("현재 시간은 ").append(now.toLocalTime()).append("입니다. 사용자의 건강 및 활동 정보를 참고하여 질문에 답해주세요. 이전 대화 맥락은 전달하지 않습니다.\n");

        if (profile != null) {
            sb.append("[건강 정보]\n");
            Optional.ofNullable(profile.getHealth()).ifPresent(health -> {
                sb.append("키 ").append(Optional.ofNullable(health.getHeight()).orElse(0.0)).append("cm, ");
                sb.append("현재 체중 ").append(Optional.ofNullable(health.getWeight()).orElse(0.0)).append("kg, 목표 체중 ")
                        .append(Optional.ofNullable(health.getGoalWeight()).orElse(0.0)).append("kg, 활동 수준 ")
                        .append(Optional.ofNullable(health.getActivityLevel()).orElse("미입력")).append("\n");
                sb.append("질환: 당뇨=").append(health.getHasDiabetes())
                        .append(", 고혈압=").append(health.getHasHypertension())
                        .append(", 고지혈증=").append(health.getHasHyperlipidemia())
                        .append(", 기타=").append(Optional.ofNullable(health.getOtherDisease()).orElse("없음")).append("\n");
                sb.append("목표: ").append(Optional.ofNullable(health.getGoal()).orElse("미입력")).append("\n");
            });
        }

        sb.append("[주간 식단 요약]\n");
        weeklyStats.getDietStats().stream()
                .filter(stat -> !stat.getDate().isAfter(date))
                .forEach(stat -> sb.append(stat.getDate()).append("(").append(stat.getDayOfWeekKr()).append(") ")
                        .append("탄수화물 ").append(stat.getTotalCarbohydrate()).append("g, 단백질 ")
                        .append(stat.getTotalProtein()).append("g, 지방 ")
                        .append(stat.getTotalFat()).append("g, 칼로리 ")
                        .append(stat.getTotalCalories()).append("kcal\n"));

        sb.append("[주간 운동 요약]\n");
        weeklyStats.getExerciseStats().stream()
                .filter(stat -> !stat.getDate().isAfter(date))
                .forEach(stat -> sb.append(stat.getDate()).append("(").append(stat.getDayOfWeekKr()).append(") ")
                        .append("운동시간 ").append(stat.getTotalDurationMinutes()).append("분, 소모 칼로리 ")
                        .append(stat.getTotalCalories()).append("kcal\n"));

        sb.append("[사용자 질문]\n").append(message).append("\n");
        sb.append("친절하고 간결하게 한국어로 답변하세요.");
        return sb.toString();
    }
}
