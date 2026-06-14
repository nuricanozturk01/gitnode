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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.gitnode.os.ai.dtos.CommitSuggestionRequest;
import dev.gitnode.os.ai.entities.AiProvider;
import dev.gitnode.os.ai.entities.UserAiSettings;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommitSuggestionService unit tests")
class CommitSuggestionServiceTest {

  @Mock private UserAiSettingsService settingsService;
  @Mock private AiProviderService mockProvider;

  @InjectMocks private CommitSuggestionService service;

  @Test
  @DisplayName("suggest returns message from AI provider")
  void suggest_returnsMessage() {
    final var tenantId = UUID.randomUUID();
    final var settings = buildSettings(tenantId);
    final var request = new CommitSuggestionRequest("+ added new feature\n- removed old code");

    when(settingsService.findEnabledSettings(tenantId)).thenReturn(Optional.of(settings));
    when(settingsService.decryptSettings(settings)).thenReturn(settings);
    when(settingsService.resolveProvider(settings)).thenReturn(mockProvider);
    when(mockProvider.complete(any(), anyString(), anyString(), anyInt()))
        .thenReturn("feat(auth): add OAuth2 login support");

    final var result = service.suggest(tenantId, request);

    assertThat(result.message()).isEqualTo("feat(auth): add OAuth2 login support");
  }

  @Test
  @DisplayName("suggest throws when AI not enabled")
  void suggest_throws_whenNotEnabled() {
    final var tenantId = UUID.randomUUID();
    when(settingsService.findEnabledSettings(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.suggest(tenantId, new CommitSuggestionRequest("diff")))
        .isInstanceOf(ErrorOccurredException.class)
        .hasMessageContaining("not enabled");
  }

  @Test
  @DisplayName("suggest trims whitespace from AI response")
  void suggest_trimsResponse() {
    final var tenantId = UUID.randomUUID();
    final var settings = buildSettings(tenantId);
    final var request = new CommitSuggestionRequest("+ change");

    when(settingsService.findEnabledSettings(tenantId)).thenReturn(Optional.of(settings));
    when(settingsService.decryptSettings(settings)).thenReturn(settings);
    when(settingsService.resolveProvider(settings)).thenReturn(mockProvider);
    when(mockProvider.complete(any(), anyString(), anyString(), anyInt()))
        .thenReturn("  fix: correct null pointer   \n");

    final var result = service.suggest(tenantId, request);

    assertThat(result.message()).isEqualTo("fix: correct null pointer");
  }

  @Test
  @DisplayName("suggest truncates very large diffs")
  void suggest_truncatesLargeDiff() {
    final var tenantId = UUID.randomUUID();
    final var settings = buildSettings(tenantId);
    final var hugeDiff = "+".repeat(20_000);
    final var request = new CommitSuggestionRequest(hugeDiff);

    when(settingsService.findEnabledSettings(tenantId)).thenReturn(Optional.of(settings));
    when(settingsService.decryptSettings(settings)).thenReturn(settings);
    when(settingsService.resolveProvider(settings)).thenReturn(mockProvider);
    when(mockProvider.complete(any(), anyString(), anyString(), anyInt()))
        .thenReturn("chore: large refactor");

    final var result = service.suggest(tenantId, request);
    assertThat(result.message()).isEqualTo("chore: large refactor");
  }

  private UserAiSettings buildSettings(final UUID tenantId) {
    final var s = new UserAiSettings();
    s.setTenantId(tenantId);
    s.setProvider(AiProvider.OPENAI);
    s.setApiKey("sk-test");
    s.setEnabled(true);
    return s;
  }
}
