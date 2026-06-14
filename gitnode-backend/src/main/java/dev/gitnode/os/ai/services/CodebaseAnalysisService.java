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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gitnode.os.ai.dtos.CodebaseAnalysisDimensionDto;
import dev.gitnode.os.ai.dtos.CodebaseAnalysisDto;
import dev.gitnode.os.ai.entities.AiCodebaseAnalysis;
import dev.gitnode.os.ai.entities.ReviewStatus;
import dev.gitnode.os.ai.repositories.AiCodebaseAnalysisRepository;
import dev.gitnode.os.events.ai.AiCodebaseAnalysisCompletedEvent;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@NullMarked
public class CodebaseAnalysisService {

  private static final Pattern RESPONSE_KEY_PATTERN = Pattern.compile("^([A-Z][A-Z0-9_]*):(.*)$");
  private static final Pattern SCORE_NUMBER_PATTERN = Pattern.compile("(\\d{1,2})");

  private static final List<DimensionDefinition> DIMENSION_DEFINITIONS =
      List.of(
          new DimensionDefinition("ARCH", "Architecture", AiCodebaseAnalysis::setArchScore),
          new DimensionDefinition("QUALITY", "Code Quality", AiCodebaseAnalysis::setQualityScore),
          new DimensionDefinition("SECURITY", "Security", AiCodebaseAnalysis::setSecurityScore),
          new DimensionDefinition("PERF", "Performance", AiCodebaseAnalysis::setPerfScore),
          new DimensionDefinition("MEMORY", "Memory Usage", AiCodebaseAnalysis::setMemoryScore),
          new DimensionDefinition(
              "SCALABILITY", "Scalability", AiCodebaseAnalysis::setScalabilityScore));

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final int MAX_FILES = CodebaseFileSampler.MAX_FILES;
  private static final int MAX_SECURITY_FILES = CodebaseFileSampler.MAX_SECURITY_FILES;
  private static final int MAX_TOTAL_SIZE = CodebaseFileSampler.MAX_TOTAL_SIZE;

  private static final int SCORE_MIN = 1;
  private static final int SCORE_MAX = 10;
  private static final int DIMENSION_COUNT = 6;
  private static final int MAX_PAGE_SIZE = 20;

  private final AiCodebaseAnalysisRepository analysisRepository;
  private final UserAiSettingsService settingsService;
  private final GitProvider gitProvider;
  private final CodebaseAnalysisRunner analysisRunner;
  private final RepoRepository repoRepository;
  private final ApplicationEventPublisher eventPublisher;

  CodebaseAnalysisService(
      final AiCodebaseAnalysisRepository analysisRepository,
      final UserAiSettingsService settingsService,
      final GitProvider gitProvider,
      @Lazy final CodebaseAnalysisRunner analysisRunner,
      final RepoRepository repoRepository,
      final ApplicationEventPublisher eventPublisher) {
    this.analysisRepository = analysisRepository;
    this.settingsService = settingsService;
    this.gitProvider = gitProvider;
    this.repoRepository = repoRepository;
    this.eventPublisher = eventPublisher;
    this.analysisRunner = analysisRunner;
  }

  @Transactional(readOnly = true)
  public Optional<CodebaseAnalysisDto> getLatest(final UUID repoId) {
    return this.analysisRepository.findFirstByRepoIdOrderByCreatedAtDesc(repoId).map(this::toDto);
  }

  @Transactional(readOnly = true)
  public PageResponse<CodebaseAnalysisDto> list(final UUID repoId, final int page, final int size) {
    final var pageRequest = PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE));
    return PageResponse.from(
        this.analysisRepository
            .findByRepoIdOrderByCreatedAtDesc(repoId, pageRequest)
            .map(this::toDto));
  }

  @Transactional
  public CodebaseAnalysisDto trigger(
      final UUID repoId,
      final String ownerUsername,
      final String repoName,
      final String branch,
      final UUID tenantId) {

    final var settings =
        this.settingsService
            .findEnabledSettings(tenantId)
            .orElseThrow(
                () -> new ErrorOccurredException("AI is not enabled. Enable it in user settings."));

    final var analysis = new AiCodebaseAnalysis();
    analysis.setRepoId(repoId);
    analysis.setBranch(branch);
    analysis.setStatus(ReviewStatus.PENDING);
    analysis.setTriggeredBy(tenantId);
    final var saved = this.analysisRepository.save(analysis);

    final var analysisId = saved.getId();
    final var settingsId = settings.getId();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            CodebaseAnalysisService.this.analysisRunner.executeAnalysis(
                analysisId, ownerUsername, repoName, branch, settingsId);
          }
        });
    return this.toDto(saved);
  }

  @Transactional
  public void executeAnalysisTask(
      final UUID analysisId,
      final String ownerUsername,
      final String repoName,
      final String branch,
      final UUID settingsId) {

    final var analysis = this.analysisRepository.findById(analysisId).orElse(null);
    if (analysis == null) return;

    try {
      final var settings = this.settingsService.findEnabledSettings(analysis.getTriggeredBy());
      if (settings.isEmpty()) {
        analysis.setStatus(ReviewStatus.SKIPPED);
        this.analysisRepository.save(analysis);
        return;
      }

      final var decrypted = this.settingsService.decryptSettings(settings.get());
      final var providerService = this.settingsService.resolveProvider(decrypted);

      final var sampler = new CodebaseFileSampler(this.gitProvider);
      final var samplingResult = sampler.sample(ownerUsername, repoName, branch);
      this.logSamplingStats(ownerUsername, repoName, samplingResult.stats());

      final var codebaseSample =
          CodebaseFileSampler.formatSample(samplingResult, MAX_FILES, MAX_TOTAL_SIZE);
      final var raw =
          providerService.complete(
              decrypted,
              AiPrompts.CODEBASE_ANALYSIS,
              codebaseSample,
              AiInputBounds.CODEBASE_MAIN_MAX_COMPLETION_TOKENS);

      final var fields = new LinkedHashMap<>(this.parseStructuredResponse(raw));
      this.mergeSecurityAnalysis(providerService, decrypted, samplingResult, fields);

      final var rawBuilder = new StringBuilder(raw);
      if (fields.containsKey("SECURITY_SCORE")) {
        rawBuilder.append("\n\n--- SECURITY PASS ---\n\n");
        this.appendSecurityFields(rawBuilder, fields);
      }
      analysis.setRawResult(rawBuilder.toString());

      this.applyParsedFields(analysis, fields);
      analysis.setStatus(ReviewStatus.COMPLETED);

    } catch (final Exception e) {
      log.error(
          "Codebase analysis failed for repo {}: {}",
          ownerUsername + "/" + repoName,
          e.getMessage());
      analysis.setStatus(ReviewStatus.FAILED);
    }
    this.analysisRepository.save(analysis);
    this.eventPublisher.publishEvent(
        new AiCodebaseAnalysisCompletedEvent(
            analysis.getId(),
            analysis.getRepoId(),
            analysis.getBranch(),
            analysis.getStatus().name(),
            analysis.getTriggeredBy(),
            ownerUsername,
            repoName));
  }

  private void mergeSecurityAnalysis(
      final AiProviderService providerService,
      final dev.gitnode.os.ai.entities.UserAiSettings decrypted,
      final CodebaseFileSampler.SamplingResult samplingResult,
      final Map<String, String> fields) {

    final var securitySample =
        CodebaseFileSampler.formatSecuritySample(
            samplingResult, MAX_SECURITY_FILES, MAX_TOTAL_SIZE / 2);
    if (securitySample.isBlank()) {
      return;
    }

    try {
      final var securityRaw =
          providerService.complete(
              decrypted,
              AiPrompts.CODEBASE_SECURITY,
              securitySample,
              AiInputBounds.CODEBASE_SECURITY_MAX_COMPLETION_TOKENS);
      final var securityFields = this.parseStructuredResponse(securityRaw);
      securityFields.forEach(
          (key, value) -> {
            if (key.startsWith("SECURITY_")) {
              fields.put(key, value);
            }
          });
    } catch (final Exception e) {
      log.warn("Dedicated security analysis pass failed: {}", e.getMessage());
    }
  }

  private void appendSecurityFields(
      final StringBuilder rawBuilder, final Map<String, String> fields) {
    for (final var key :
        List.of("SECURITY_SCORE", "SECURITY_REASON", "SECURITY_ISSUES", "SECURITY_FIX")) {
      final var value = fields.get(key);
      if (value != null && !value.isBlank()) {
        rawBuilder.append(key).append(':').append(value).append('\n');
      }
    }
  }

  private void logSamplingStats(
      final String owner, final String repoName, final CodebaseFileSampler.SamplingStats stats) {
    log.info(
        "Codebase sampling for {}/{}: scanned={}, analyzable={}, skippedPaths={}, loaded={},"
            + " skippedOversized={}, skippedBinary={}, scanLimitReached={}",
        owner,
        repoName,
        stats.scannedEntries,
        stats.analyzableFilesSeen,
        stats.skippedPaths,
        stats.loadedFiles,
        stats.skippedOversized,
        stats.skippedBinary,
        stats.scanLimitReached);
  }

  private void applyParsedFields(
      final AiCodebaseAnalysis analysis, final Map<String, String> fields) {

    for (final var definition : DIMENSION_DEFINITIONS) {
      final var score = this.parseScoreValue(fields.get(definition.prefix() + "_SCORE"));
      definition.scoreSetter().accept(analysis, score);
    }

    analysis.setSummary(this.normalizeText(fields.get("SUMMARY")));
    analysis.setRecommendations(this.normalizeText(fields.get("RECOMMENDATIONS")));

    final var dimensions = this.buildDimensions(fields, analysis);
    analysis.setDimensionDetails(this.serializeDimensions(dimensions));
    this.applyOverallScore(analysis);
  }

  private void applyOverallScore(final AiCodebaseAnalysis analysis) {
    if (analysis.getArchScore() == null
        || analysis.getQualityScore() == null
        || analysis.getPerfScore() == null
        || analysis.getMemoryScore() == null
        || analysis.getScalabilityScore() == null
        || analysis.getSecurityScore() == null) {
      return;
    }
    final var avg =
        (analysis.getArchScore()
                + analysis.getQualityScore()
                + analysis.getPerfScore()
                + analysis.getMemoryScore()
                + analysis.getScalabilityScore()
                + analysis.getSecurityScore())
            / DIMENSION_COUNT;
    analysis.setOverallScore((short) avg);
  }

  private Map<String, String> parseStructuredResponse(final String raw) {
    final var result = new LinkedHashMap<String, String>();
    final var state = new ParseState();
    for (final var line : raw.split("\n", -1)) {
      this.processResponseLine(state, result, line);
    }
    if (state.currentKey != null) {
      result.put(state.currentKey, state.valueBuilder.toString().strip());
    }
    return result;
  }

  private void processResponseLine(
      final ParseState state, final Map<String, String> result, final String line) {
    final var matcher = RESPONSE_KEY_PATTERN.matcher(line.strip());
    if (matcher.matches()) {
      if (state.currentKey != null) {
        result.put(state.currentKey, state.valueBuilder.toString().strip());
        state.valueBuilder.setLength(0);
      }
      state.currentKey = matcher.group(1);
      final var inlineValue = matcher.group(2);
      if (inlineValue != null && !inlineValue.isBlank()) {
        state.valueBuilder.append(inlineValue.strip());
      }
    } else if (state.currentKey != null) {
      if (!state.valueBuilder.isEmpty()) {
        state.valueBuilder.append('\n');
      }
      state.valueBuilder.append(line);
    }
  }

  private List<CodebaseAnalysisDimensionDto> buildDimensions(
      final Map<String, String> fields, final AiCodebaseAnalysis analysis) {
    final var dimensions = new ArrayList<CodebaseAnalysisDimensionDto>();
    for (final var definition : DIMENSION_DEFINITIONS) {
      final var prefix = definition.prefix();
      dimensions.add(
          new CodebaseAnalysisDimensionDto(
              prefix.toLowerCase(),
              definition.label(),
              this.scoreFor(definition, analysis, fields, prefix),
              this.normalizeText(fields.get(prefix + "_REASON")),
              this.normalizeIssueText(fields.get(prefix + "_ISSUES")),
              this.normalizeText(fields.get(prefix + "_FIX"))));
    }
    return List.copyOf(dimensions);
  }

  private @Nullable Short scoreFor(
      final DimensionDefinition definition,
      final AiCodebaseAnalysis analysis,
      final Map<String, String> fields,
      final String prefix) {
    final var parsed = this.parseScoreValue(fields.get(prefix + "_SCORE"));

    if (parsed != null) {
      return parsed;
    }

    return this.scoreFromAnalysis(prefix, analysis);
  }

  private @Nullable Short scoreFromAnalysis(
      final String prefix, final AiCodebaseAnalysis analysis) {
    return switch (prefix) {
      case "ARCH" -> analysis.getArchScore();
      case "QUALITY" -> analysis.getQualityScore();
      case "PERF" -> analysis.getPerfScore();
      case "MEMORY" -> analysis.getMemoryScore();
      case "SCALABILITY" -> analysis.getScalabilityScore();
      case "SECURITY" -> analysis.getSecurityScore();
      default -> null;
    };
  }

  private String serializeDimensions(final List<CodebaseAnalysisDimensionDto> dimensions) {
    try {
      return OBJECT_MAPPER.writeValueAsString(dimensions);
    } catch (final IOException e) {
      throw new ErrorOccurredException("Failed to serialize analysis dimensions");
    }
  }

  private List<CodebaseAnalysisDimensionDto> deserializeDimensions(
      final AiCodebaseAnalysis analysis) {
    final var json = analysis.getDimensionDetails();
    if (json != null && !json.isBlank()) {
      try {
        return OBJECT_MAPPER.readValue(
            json, new TypeReference<List<CodebaseAnalysisDimensionDto>>() {});
      } catch (final IOException e) {
        log.warn("Failed to deserialize dimension details for analysis {}", analysis.getId());
      }
    }
    return this.legacyDimensions(analysis);
  }

  private List<CodebaseAnalysisDimensionDto> legacyDimensions(final AiCodebaseAnalysis analysis) {
    return DIMENSION_DEFINITIONS.stream()
        .map(
            definition ->
                new CodebaseAnalysisDimensionDto(
                    definition.prefix().toLowerCase(),
                    definition.label(),
                    this.scoreFor(definition, analysis, Map.of(), definition.prefix()),
                    null,
                    null,
                    null))
        .toList();
  }

  private @Nullable Short parseScoreValue(final @Nullable String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    final Matcher matcher = SCORE_NUMBER_PATTERN.matcher(value.strip());

    if (!matcher.find()) {
      return null;
    }

    try {
      final var parsed = Short.parseShort(matcher.group(1));
      return (parsed >= SCORE_MIN && parsed <= SCORE_MAX) ? parsed : null;
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private @Nullable String normalizeText(final @Nullable String value) {
    if (value == null) {
      return null;
    }
    final var normalized = value.strip();
    return normalized.isEmpty() ? null : normalized;
  }

  private @Nullable String normalizeIssueText(final @Nullable String value) {
    final var normalized = this.normalizeText(value);
    if (normalized == null || "NONE".equalsIgnoreCase(normalized)) {
      return null;
    }
    return normalized;
  }

  private CodebaseAnalysisDto toDto(final AiCodebaseAnalysis a) {
    return new CodebaseAnalysisDto(
        a.getId(),
        a.getBranch(),
        a.getStatus(),
        a.getArchScore(),
        a.getQualityScore(),
        a.getPerfScore(),
        a.getMemoryScore(),
        a.getScalabilityScore(),
        a.getSecurityScore(),
        a.getOverallScore(),
        a.getSummary(),
        a.getRecommendations(),
        this.deserializeDimensions(a),
        a.getCreatedAt());
  }

  private record DimensionDefinition(
      String prefix, String label, BiConsumer<AiCodebaseAnalysis, Short> scoreSetter) {}

  private static final class ParseState {
    @Nullable String currentKey;
    final StringBuilder valueBuilder = new StringBuilder();
  }
}
