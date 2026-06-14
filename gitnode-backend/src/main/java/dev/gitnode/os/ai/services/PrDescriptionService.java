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

import static dev.gitnode.os.shared.util.FileDiffParser.prepareTreeParser;

import dev.gitnode.os.ai.dtos.PrDescriptionRequest;
import dev.gitnode.os.ai.dtos.PrDescriptionResponse;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class PrDescriptionService {

  private final UserAiSettingsService settingsService;
  private final GitProvider gitProvider;

  @Transactional(readOnly = true)
  public PrDescriptionResponse generate(final UUID tenantId, final PrDescriptionRequest request)
      throws IOException {
    final var settings =
        this.settingsService
            .findEnabledSettings(tenantId)
            .orElseThrow(
                () -> new ErrorOccurredException("AI is not enabled. Enable it in user settings."));

    final var decrypted = this.settingsService.decryptSettings(settings);
    final var providerService = this.settingsService.resolveProvider(decrypted);

    final var diffResult = this.buildDiffText(request);
    final var raw =
        providerService.complete(
            decrypted,
            AiPrompts.PR_DESCRIPTION,
            diffResult.text(),
            AiInputBounds.PR_DESC_MAX_COMPLETION_TOKENS);
    return this.parseResponse(raw);
  }

  private AiDiffFormatter.DiffBuildResult buildDiffText(final PrDescriptionRequest request)
      throws IOException {
    try (final var gitRepo = this.gitProvider.open(request.owner(), request.repo())) {
      final var sourceRef = gitRepo.resolve(Constants.R_HEADS + request.sourceBranch());
      final var targetRef = gitRepo.resolve(Constants.R_HEADS + request.targetBranch());
      if (sourceRef == null || targetRef == null) {
        return new AiDiffFormatter.DiffBuildResult(
            "Branch not found", new AiDiffFormatter.DiffBuildStats());
      }
      final var sourceTree = prepareTreeParser(gitRepo, sourceRef);
      final var targetTree = prepareTreeParser(gitRepo, targetRef);

      return AiDiffFormatter.buildBoundedDiff(
          gitRepo,
          targetTree,
          sourceTree,
          AiInputBounds.PR_DESC_MAX_FILES,
          AiInputBounds.PR_DESC_MAX_DIFF_CHARS);
    }
  }

  private PrDescriptionResponse parseResponse(final String raw) {
    final var lines = raw.strip().split("\n", -1);
    String title = "";
    final var descBuilder = new StringBuilder();
    boolean inDescription = false;

    for (final var line : lines) {
      if (line.startsWith("TITLE:")) {
        title = line.substring("TITLE:".length()).strip();
      } else if (line.startsWith("DESCRIPTION:")) {
        inDescription = true;
      } else if (inDescription) {
        descBuilder.append(line).append("\n");
      }
    }

    if (title.isBlank()) {
      title = raw.lines().findFirst().orElse("PR changes").strip();
    }

    return new PrDescriptionResponse(title, descBuilder.toString().strip());
  }
}
