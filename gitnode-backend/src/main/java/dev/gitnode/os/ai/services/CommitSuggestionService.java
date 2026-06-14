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
package dev.gitnode.os.ai.services;

import static dev.gitnode.os.ai.services.AiDiffFormatter.boundClientDiff;
import static dev.gitnode.os.ai.services.AiInputBounds.COMMIT_MAX_COMPLETION_TOKENS;
import static dev.gitnode.os.ai.services.AiInputBounds.COMMIT_MAX_DIFF_CHARS;
import static dev.gitnode.os.ai.services.AiPrompts.COMMIT_SUGGESTION;

import dev.gitnode.os.ai.dtos.CommitSuggestionRequest;
import dev.gitnode.os.ai.dtos.CommitSuggestionResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
public class CommitSuggestionService {

  private final UserAiSettingsService settingsService;

  @Transactional(readOnly = true)
  public CommitSuggestionResponse suggest(
      final UUID tenantId, final CommitSuggestionRequest request) {

    final var settings =
        this.settingsService
            .findEnabledSettings(tenantId)
            .orElseThrow(
                () -> new ErrorOccurredException("AI is not enabled. Enable it in user settings."));

    final var decrypted = this.settingsService.decryptSettings(settings);
    final var providerService = this.settingsService.resolveProvider(decrypted);

    final var boundedDiff = boundClientDiff(request.diff(), COMMIT_MAX_DIFF_CHARS);

    final var message =
        providerService.complete(
            decrypted, COMMIT_SUGGESTION, boundedDiff, COMMIT_MAX_COMPLETION_TOKENS);

    return new CommitSuggestionResponse(message.strip());
  }
}
