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
package com.nuricanozturk.originhub.shared.audit.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.audit.entities.AuditLog;
import com.nuricanozturk.originhub.shared.audit.repositories.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController unit tests")
class AuditLogControllerTest {

  @Mock private AuditLogRepository auditLogRepository;

  @InjectMocks private AuditLogController auditLogController;

  private static AuditLog entry(String actor, String action) {
    AuditLog log = new AuditLog();
    log.setActorUsername(actor);
    log.setAction(action);
    return log;
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs")
  class GetAll {

    @Test
    @DisplayName("returns HTTP 200 with page of audit logs")
    void getAll_returnsPage() {
      var page = new PageImpl<>(List.of(entry("alice", "CREATE_REPO")));
      when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

      var response = auditLogController.getAll(0, 20);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("caps page size at 50 when larger value requested")
    void getAll_capsPageSize_at50() {
      when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

      auditLogController.getAll(0, 200);

      verify(auditLogRepository).findAll(argThat((Pageable p) -> p.getPageSize() == 50));
    }

    @Test
    @DisplayName("uses requested page size when at or below 50")
    void getAll_usesRequestedSize_whenBelowCap() {
      when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

      auditLogController.getAll(1, 25);

      verify(auditLogRepository)
          .findAll(argThat((Pageable p) -> p.getPageSize() == 25 && p.getPageNumber() == 1));
    }
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs/by-actor")
  class GetByActor {

    @Test
    @DisplayName("returns page filtered by actor username")
    void getByActor_returnsFilteredPage() {
      var page = new PageImpl<>(List.of(entry("bob", "DELETE_REPO")));
      when(auditLogRepository.findAllByActorUsernameOrderByOccurredAtDesc(
              eq("bob"), any(Pageable.class)))
          .thenReturn(page);

      var response = auditLogController.getByActor("bob", 0, 10);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent().get(0).getActorUsername()).isEqualTo("bob");
    }

    @Test
    @DisplayName("caps page size at 50 for by-actor query")
    void getByActor_capsPageSize_at50() {
      when(auditLogRepository.findAllByActorUsernameOrderByOccurredAtDesc(
              any(), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      auditLogController.getByActor("alice", 0, 500);

      verify(auditLogRepository)
          .findAllByActorUsernameOrderByOccurredAtDesc(
              eq("alice"), argThat((Pageable p) -> p.getPageSize() == 50));
    }
  }

  @Nested
  @DisplayName("GET /api/admin/audit-logs/recent")
  class GetRecent {

    @Test
    @DisplayName("returns page of recent entries within requested hours")
    void getRecent_returnsPage_withinRequestedHours() {
      var page = new PageImpl<>(List.of(entry("alice", "MERGE_PR")));
      when(auditLogRepository.findAllByOccurredAtAfterOrderByOccurredAtDesc(
              any(Instant.class), any(Pageable.class)))
          .thenReturn(page);

      var response = auditLogController.getRecent(24, 0, 10);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("caps lastHours at 720 when larger value supplied")
    void getRecent_capsLastHours_at720() {
      Instant before = Instant.now();
      when(auditLogRepository.findAllByOccurredAtAfterOrderByOccurredAtDesc(
              any(Instant.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      auditLogController.getRecent(9999, 0, 10);

      Instant after = Instant.now();
      Instant maxCutoff = before.minus(720, ChronoUnit.HOURS);

      verify(auditLogRepository)
          .findAllByOccurredAtAfterOrderByOccurredAtDesc(
              argThat(
                  since ->
                      !since.isBefore(maxCutoff.minusSeconds(5))
                          && !since.isAfter(after.minus(720, ChronoUnit.HOURS).plusSeconds(5))),
              any(Pageable.class));
    }

    @Test
    @DisplayName("uses exact lastHours when within 720 limit")
    void getRecent_usesExactHours_whenWithinLimit() {
      Instant before = Instant.now().minus(12, ChronoUnit.HOURS);
      when(auditLogRepository.findAllByOccurredAtAfterOrderByOccurredAtDesc(
              any(Instant.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of()));

      auditLogController.getRecent(12, 0, 10);

      verify(auditLogRepository)
          .findAllByOccurredAtAfterOrderByOccurredAtDesc(
              argThat(since -> Math.abs(since.toEpochMilli() - before.toEpochMilli()) < 2000),
              any(Pageable.class));
    }
  }
}
