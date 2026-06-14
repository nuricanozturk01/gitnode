/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.ai.services.providers;

import dev.gitnode.os.ai.entities.AiProvider;
import dev.gitnode.os.ai.entities.UserAiSettings;
import dev.gitnode.os.ai.services.AiProviderService;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class GeminiProviderService implements AiProviderService {

  private static final String DEFAULT_MODEL = "gemini-2.0-flash";
  private static final String GEMINI_OPENAI_BASE_URL =
      "https://generativelanguage.googleapis.com/v1beta/openai/";
  private static final int DEFAULT_MAX_COMPLETION_TOKENS = 1024;
  private static final double DEFAULT_TEMPERATURE = 0.3;

  @Override
  public AiProvider provider() {
    return AiProvider.GEMINI;
  }

  @Override
  @CircuitBreaker(name = "ai-gemini", fallbackMethod = "fallback")
  public String complete(
      final UserAiSettings settings, final String systemPrompt, final String userPrompt) {
    return this.complete(settings, systemPrompt, userPrompt, DEFAULT_MAX_COMPLETION_TOKENS);
  }

  @Override
  @CircuitBreaker(name = "ai-gemini", fallbackMethod = "fallbackWithTokens")
  public String complete(
      final UserAiSettings settings,
      final String systemPrompt,
      final String userPrompt,
      final int maxCompletionTokens) {
    final var apiKey = settings.getApiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new ErrorOccurredException("Gemini API key not configured");
    }
    final var model =
        (settings.getModel() != null && !settings.getModel().isBlank())
            ? settings.getModel()
            : DEFAULT_MODEL;

    final var options =
        OpenAiChatOptions.builder()
            .apiKey(apiKey)
            .baseUrl(GEMINI_OPENAI_BASE_URL)
            .model(model)
            .maxCompletionTokens(maxCompletionTokens)
            .temperature(DEFAULT_TEMPERATURE)
            .build();

    final var chatModel = OpenAiChatModel.builder().options(options).build();
    return AiChatCompleter.complete(chatModel, systemPrompt, userPrompt);
  }

  @SuppressWarnings("unused")
  private String fallbackWithTokens(
      final UserAiSettings settings,
      final String systemPrompt,
      final String userPrompt,
      final int maxCompletionTokens,
      final Throwable t) {
    return this.fallback(settings, systemPrompt, userPrompt, t);
  }

  @SuppressWarnings("unused")
  private String fallback(
      final UserAiSettings settings,
      final String systemPrompt,
      final String userPrompt,
      final Throwable t) {
    log.warn("Gemini circuit breaker open: {}", t.getMessage());
    throw new ErrorOccurredException("AI service temporarily unavailable. Please try again later.");
  }
}
