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

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.model.ScheduleModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Fires {@code on.schedule} workflows. Every minute it checks which cron entries are due; a Redis
 * lock per (workflow, cron, minute) makes sure only one backend instance starts the run.
 *
 * <p>Cron expressions use GitHub's five fields (minute hour day month weekday) and are evaluated in
 * UTC unless the entry has a {@code timezone}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class WorkflowScheduler {

  private static final String LOCK_PREFIX = "gitnode:actions:schedule:";
  private static final Duration LOCK_TTL = Duration.ofMinutes(10);

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowParserService parserService;
  private final WorkflowTriggerService triggerService;
  private final StringRedisTemplate redisTemplate;

  @Scheduled(cron = "0 * * * * *")
  public void tick() {
    this.tick(Instant.now());
  }

  void tick(final Instant now) {
    final Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
    for (final var definition : this.definitionRepository.findAllWithSchedule()) {
      for (final var schedule : this.schedulesOf(definition)) {
        if (isDue(schedule, minute)) {
          this.fire(definition, schedule, minute);
        }
      }
    }
  }

  private List<ScheduleModel> schedulesOf(final WorkflowDefinition definition) {
    final var model = this.parserService.parseFromContent(definition.getContent());
    if (model == null || model.on() == null || model.on().schedule() == null) {
      return List.of();
    }
    return model.on().schedule();
  }

  @SuppressWarnings(
      "checkstyle:IllegalCatch") // scheduler loop: one bad workflow must not stop the rest
  private void fire(
      final WorkflowDefinition definition, final ScheduleModel schedule, final Instant minute) {

    final String key =
        LOCK_PREFIX
            + definition.getId()
            + ":"
            + Integer.toHexString(String.valueOf(schedule.cron()).hashCode())
            + ":"
            + minute.getEpochSecond();
    if (!Boolean.TRUE.equals(this.redisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL))) {
      return; // another instance already started this run
    }
    try {
      this.triggerService.triggerOnSchedule(
          definition.getRepoId(), definition.getFilePath(), String.valueOf(schedule.cron()));
    } catch (final RuntimeException ex) {
      log.error(
          "Scheduled run failed: workflow={} cron={}", definition.getFilePath(), schedule, ex);
    }
  }

  /** True when {@code minute} is one of the times the schedule fires. */
  static boolean isDue(final ScheduleModel schedule, final Instant minute) {
    final String cron = schedule.cron();
    if (cron == null || cron.isBlank()) {
      return false;
    }
    try {
      // GitHub cron has five fields; Spring's has a leading seconds field.
      final var expression = CronExpression.parse("0 " + cron.trim());
      final var before = ZonedDateTime.ofInstant(minute.minusSeconds(1), zoneOf(schedule));
      final var next = expression.next(before);
      return next != null && next.toInstant().equals(minute);
    } catch (final IllegalArgumentException ex) {
      log.warn("Ignoring invalid cron '{}': {}", cron, ex.getMessage());
      return false;
    }
  }

  private static ZoneId zoneOf(final ScheduleModel schedule) {
    final String timezone = schedule.timezone();
    if (timezone == null || timezone.isBlank()) {
      return ZoneOffset.UTC;
    }
    try {
      return ZoneId.of(timezone.trim());
    } catch (final DateTimeException ex) {
      log.warn("Unknown timezone '{}', using UTC", timezone);
      return ZoneOffset.UTC;
    }
  }
}
