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
package dev.gitnode.os.notification.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.notification.services.NotificationService;
import dev.gitnode.os.notification.services.NotificationSseRegistry;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {
  @Mock private NotificationService notificationService;
  @Mock private NotificationSseRegistry sseRegistry;
  @Mock private JwtUtils jwtUtils;
  @Mock private TenantRepository tenantRepository;
  @InjectMocks private NotificationController controller;

  @Test
  void streamRejectsDisabledAccountUsingQueryToken() {
    UUID id = UUID.randomUUID();
    Tenant tenant = new Tenant();
    tenant.setEnabled(false);
    when(jwtUtils.extractUserId("token")).thenReturn(id);
    when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

    assertThatThrownBy(() -> controller.stream(null, "token"))
        .isInstanceOf(AccessNotAllowedException.class);
    verify(sseRegistry, never()).subscribe(id);
  }

  @Test
  void streamAcceptsEnabledAccount() {
    UUID id = UUID.randomUUID();
    Tenant tenant = new Tenant();
    SseEmitter emitter = new SseEmitter();
    when(jwtUtils.extractUserId("token")).thenReturn(id);
    when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
    when(sseRegistry.subscribe(id)).thenReturn(emitter);

    assertThat(controller.stream(null, "token")).isSameAs(emitter);
  }
}
