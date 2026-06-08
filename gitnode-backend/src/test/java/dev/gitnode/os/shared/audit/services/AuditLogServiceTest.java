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
package dev.gitnode.os.shared.audit.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.audit.entities.AuditLog;
import dev.gitnode.os.shared.audit.repositories.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService unit tests")
class AuditLogServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  @InjectMocks private AuditLogService auditLogService;

  @Test
  @DisplayName("log saves entry with all fields populated")
  void log_savesEntry_withAllFields() {
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    auditLogService.log("alice", "CREATE_REPO", "REPO", "uuid-123", "details", "10.0.0.1");

    verify(auditLogRepository)
        .save(
            argThat(
                entry ->
                    "alice".equals(entry.getActorUsername())
                        && "CREATE_REPO".equals(entry.getAction())
                        && "REPO".equals(entry.getEntityType())
                        && "uuid-123".equals(entry.getEntityId())
                        && "details".equals(entry.getDetails())
                        && "10.0.0.1".equals(entry.getIpAddress())));
  }

  @Test
  @DisplayName("log saves entry when optional fields are null")
  void log_savesEntry_withNullOptionalFields() {
    when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

    auditLogService.log(null, "DELETE_REPO", null, null, null, null);

    verify(auditLogRepository)
        .save(
            argThat(
                entry ->
                    entry.getActorUsername() == null
                        && "DELETE_REPO".equals(entry.getAction())
                        && entry.getEntityType() == null
                        && entry.getEntityId() == null));
  }

  @Test
  @DisplayName("log swallows exception when repository throws")
  void log_swallowsException_whenRepositoryFails() {
    when(auditLogRepository.save(any(AuditLog.class)))
        .thenThrow(new RuntimeException("DB unavailable"));

    assertThatCode(() -> auditLogService.log("alice", "CREATE_REPO", "REPO", "1", null, null))
        .doesNotThrowAnyException();
  }
}
