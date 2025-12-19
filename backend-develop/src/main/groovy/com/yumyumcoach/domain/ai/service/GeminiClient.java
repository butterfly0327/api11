package com.yumyumcoach.domain.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yumyumcoach.global.exception.BusinessException;
import com.yumyumcoach.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GeminiClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gms.api.url:https://gms.ssafy.io/gmsapi/generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiUrl;

    @Value("${gms.api.key:}")
    private String geminiApiKey;

    public String generate(String prompt) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini API 키가 설정되지 않았습니다.");
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(parts));
        body.put("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(buildUrl(), entity, JsonNode.class);

            JsonNode root = response.getBody();
            String text = extractText(root);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini 응답을 읽을 수 없습니다.");
            }
            return text;
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini 호출에 실패했습니다.");
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Gemini 요청 처리 중 오류가 발생했습니다.");
        }
    }

    private String buildUrl() {
        if (geminiUrl.contains("?")) {
            return geminiUrl + "&key=" + geminiApiKey;
        }
        return geminiUrl + "?key=" + geminiApiKey;
    }

    private String extractText(JsonNode root) {
        if (root == null || !root.has("candidates") || !root.get("candidates").isArray()) {
            return null;
        }
        JsonNode candidates = root.get("candidates");
        for (JsonNode candidate : candidates) {
            JsonNode content = candidate.path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    String text = Optional.ofNullable(part.path("text").asText(null)).orElse(null);
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return null;
    }
}
