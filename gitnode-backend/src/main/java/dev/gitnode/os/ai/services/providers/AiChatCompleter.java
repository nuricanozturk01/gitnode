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

import dev.gitnode.os.ai.services.AiInputBounds;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

@Slf4j
@NullMarked
final class AiChatCompleter {

  private AiChatCompleter() {}

  static String complete(
      final ChatModel chatModel, final String systemPrompt, final String userPrompt) {
    final var boundedPrompt = AiInputBounds.boundUserPrompt(userPrompt);
    try {
      return CompletableFuture.supplyAsync(
              () -> invokeProvider(chatModel, systemPrompt, boundedPrompt))
          .get(AiInputBounds.LLM_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (final TimeoutException e) {
      throw new ErrorOccurredException(
          "AI provider request timed out after "
              + AiInputBounds.LLM_CALL_TIMEOUT_SECONDS
              + " seconds");
    } catch (final ExecutionException e) {
      final var cause = e.getCause();
      if (cause instanceof ErrorOccurredException errorOccurred) {
        throw errorOccurred;
      }
      throw new ErrorOccurredException(
          "AI provider request failed: " + summarizeError(cause instanceof Exception ex ? ex : e));
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ErrorOccurredException("AI provider request was interrupted");
    }
  }

  private static String invokeProvider(
      final ChatModel chatModel, final String systemPrompt, final String boundedPrompt) {
    try {
      final var response =
          chatModel.call(
              new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(boundedPrompt))));
      final var result = response.getResult();
      if (result == null) {
        throw new ErrorOccurredException("Empty response from AI provider");
      }
      final var text = result.getOutput().getText();
      if (!StringUtils.hasText(text)) {
        throw new ErrorOccurredException("Empty response from AI provider");
      }
      return text;
    } catch (final ErrorOccurredException e) {
      throw e;
    } catch (final Exception e) {
      log.warn("AI provider call failed: {}", e.getMessage());
      throw new ErrorOccurredException("AI provider request failed: " + summarizeError(e));
    }
  }

  private static String summarizeError(final Exception e) {
    final var message = e.getMessage();
    if (StringUtils.hasText(message)) {
      return message;
    }
    final var cause = e.getCause();
    if (cause != null && StringUtils.hasText(cause.getMessage())) {
      return cause.getMessage();
    }
    return "unexpected error";
  }
}
