package io.routepickapi.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.routepickapi.dto.course.CourseCurationResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiserLlmClient {

    private static final String JSON_RESPONSE_FORMAT = "json_object";

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${llm.api-url:}")
    private String apiUrl;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:gpt-4o-mini}")
    private String model;

    public Optional<CourseCurationResponse> requestCuration(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("LLM API config missing");
            return Optional.empty();
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
            model,
            List.of(new ChatMessage("user", prompt)),
            0.4,
            new ResponseFormat(JSON_RESPONSE_FORMAT)
        );

        try {
            ChatCompletionResponse response = restClient.post()
                .uri(apiUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                return Optional.empty();
            }

            ChatMessage message = response.choices().getFirst().message();
            if (message == null || message.content() == null || message.content().isBlank()) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(message.content(), CourseCurationResponse.class));
        } catch (RestClientException ex) {
            log.warn("LLM request failed", ex);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("LLM response parse failed", ex);
            return Optional.empty();
        }
    }

    private record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature,
        @JsonProperty("response_format") ResponseFormat responseFormat
    ) {
    }

    private record ChatCompletionResponse(List<Choice> choices) {
    }

    private record Choice(ChatMessage message) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ResponseFormat(String type) {
    }
}
