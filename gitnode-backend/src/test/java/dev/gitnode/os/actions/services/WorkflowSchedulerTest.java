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
package dev.gitnode.os.actions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.model.ScheduleModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowScheduler")
class WorkflowSchedulerTest {

  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowTriggerService triggerService;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOps;

  private WorkflowScheduler scheduler;

  private static final UUID REPO_ID = UUID.randomUUID();
  private static final String YAML =
      """
      on:
        schedule:
          - cron: "30 13 * * *"
      jobs: {}
      """;

  @BeforeEach
  void setUp() {
    this.scheduler =
        new WorkflowScheduler(
            this.definitionRepository, this.parserService, this.triggerService, this.redisTemplate);
  }

  private static Instant at(final String isoUtc) {
    return Instant.parse(isoUtc);
  }

  private static ScheduleModel cron(final String expr) {
    return new ScheduleModel(expr, null);
  }

  @Test
  @DisplayName("five-field cron fires on the matching minute only")
  void isDue_matchingMinuteOnly() {
    assertThat(WorkflowScheduler.isDue(cron("30 13 * * *"), at("2026-11-04T13:30:00Z"))).isTrue();
    assertThat(WorkflowScheduler.isDue(cron("30 13 * * *"), at("2026-11-04T13:31:00Z"))).isFalse();
    assertThat(WorkflowScheduler.isDue(cron("30 13 * * *"), at("2026-11-04T14:30:00Z"))).isFalse();
  }

  @Test
  @DisplayName("steps, lists, ranges and weekdays work")
  void isDue_cronSyntax() {
    final var fiveMinutes = cron("*/5 * * * *");
    assertThat(WorkflowScheduler.isDue(fiveMinutes, at("2026-11-04T10:15:00Z"))).isTrue();
    assertThat(WorkflowScheduler.isDue(fiveMinutes, at("2026-11-04T10:16:00Z"))).isFalse();
    final var weekdays = cron("0 9 * * MON-FRI"); // 2026-11-04 is a Wednesday
    assertThat(WorkflowScheduler.isDue(weekdays, at("2026-11-04T09:00:00Z"))).isTrue();
    assertThat(WorkflowScheduler.isDue(weekdays, at("2026-11-07T09:00:00Z"))).isFalse(); // Saturday
    assertThat(WorkflowScheduler.isDue(cron("0,30 8-9 * * *"), at("2026-11-04T09:30:00Z")))
        .isTrue();
  }

  @Test
  @DisplayName("timezone shifts the firing time")
  void isDue_timezone() {
    final var istanbul = new ScheduleModel("0 16 * * *", "Europe/Istanbul"); // UTC+3
    assertThat(WorkflowScheduler.isDue(istanbul, at("2026-11-04T13:00:00Z"))).isTrue();
    assertThat(WorkflowScheduler.isDue(istanbul, at("2026-11-04T16:00:00Z"))).isFalse();
  }

  @Test
  @DisplayName("an unknown timezone falls back to UTC")
  void isDue_unknownTimezone() {
    final var bad = new ScheduleModel("0 16 * * *", "Mars/Olympus");
    assertThat(WorkflowScheduler.isDue(bad, at("2026-11-04T16:00:00Z"))).isTrue();
  }

  @Test
  @DisplayName("invalid or empty cron never fires and never throws")
  void isDue_invalid() {
    final var minute = at("2026-11-04T13:30:00Z");
    assertThat(WorkflowScheduler.isDue(cron("not a cron"), minute)).isFalse();
    assertThat(WorkflowScheduler.isDue(cron("* * * * * *"), minute)).isFalse();
    assertThat(WorkflowScheduler.isDue(cron("61 * * * *"), minute)).isFalse();
    assertThat(WorkflowScheduler.isDue(cron(""), minute)).isFalse();
    assertThat(WorkflowScheduler.isDue(new ScheduleModel(null, null), minute)).isFalse();
  }

  private WorkflowDefinition definition() throws Exception {
    final var definition = new WorkflowDefinition();
    definition.setId(UUID.randomUUID());
    definition.setRepoId(REPO_ID);
    definition.setFilePath(".gitnode/workflows/nightly.yml");
    definition.setContent(YAML);
    when(this.definitionRepository.findAllWithSchedule()).thenReturn(List.of(definition));
    when(this.parserService.parseFromContent(YAML))
        .thenReturn(
            WorkflowParserService.YAML_MAPPER.readValue(
                YAML, dev.gitnode.os.actions.model.WorkflowModel.class));
    return definition;
  }

  @Test
  @DisplayName("tick starts a due schedule exactly once")
  void tick_firesDueSchedule() throws Exception {
    final var definition = this.definition();
    when(this.redisTemplate.opsForValue()).thenReturn(this.valueOps);
    when(this.valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

    this.scheduler.tick(at("2026-11-04T13:30:20Z")); // seconds are ignored

    verify(this.triggerService).triggerOnSchedule(REPO_ID, definition.getFilePath(), "30 13 * * *");
  }

  @Test
  @DisplayName("tick does nothing when the schedule is not due")
  void tick_notDue() throws Exception {
    this.definition();

    this.scheduler.tick(at("2026-11-04T13:31:00Z"));

    verify(this.triggerService, never()).triggerOnSchedule(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("another instance holding the lock prevents a duplicate run")
  void tick_lockHeldElsewhere() throws Exception {
    this.definition();
    when(this.redisTemplate.opsForValue()).thenReturn(this.valueOps);
    when(this.valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

    this.scheduler.tick(at("2026-11-04T13:30:00Z"));

    verify(this.triggerService, never()).triggerOnSchedule(any(), anyString(), anyString());
  }

  @Test
  @DisplayName("a failing run does not stop the scheduler")
  void tick_survivesFailure() throws Exception {
    this.definition();
    when(this.redisTemplate.opsForValue()).thenReturn(this.valueOps);
    when(this.valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
        .when(this.triggerService)
        .triggerOnSchedule(any(), anyString(), anyString());

    this.scheduler.tick(at("2026-11-04T13:30:00Z")); // must not throw

    verify(this.triggerService).triggerOnSchedule(any(), anyString(), anyString());
  }
}
