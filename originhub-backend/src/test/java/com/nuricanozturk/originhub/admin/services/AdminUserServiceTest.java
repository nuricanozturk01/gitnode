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
package com.nuricanozturk.originhub.admin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.errorhandling.exceptions.BadRequestException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserService unit tests")
class AdminUserServiceTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private PlatformAdminService platformAdminService;

  @InjectMocks private AdminUserService adminUserService;

  @Test
  @DisplayName("setEnabled disables regular user")
  void setEnabled_disablesRegularUser() {

    final var tenant = tenant("alice", true);
    when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
    when(tenantRepository.save(any(Tenant.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(platformAdminService.isBootstrapAdminUsername("alice")).thenReturn(false);

    final var result = adminUserService.setEnabled(tenant.getId(), false);

    assertThat(result.enabled()).isFalse();
    verify(tenantRepository).save(tenant);
  }

  @Test
  @DisplayName("setEnabled rejects disabling bootstrap admin")
  void setEnabled_throws_whenBootstrapAdmin() {

    final var tenant = tenant("admin", true);
    when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
    when(platformAdminService.isBootstrapAdminUsername("admin")).thenReturn(true);

    assertThatThrownBy(() -> adminUserService.setEnabled(tenant.getId(), false))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("cannotDisableBootstrapAdmin");
  }

  @Test
  @DisplayName("listUsers returns all users when query is blank")
  void listUsers_returnsAll_whenQueryBlank() {
    var tenant = tenant("alice", true);
    when(tenantRepository.findAllByOrderByCreatedAtDesc(any()))
        .thenReturn(new PageImpl<>(List.of(tenant)));

    var page = adminUserService.listUsers(PageRequest.of(0, 10), "  ");

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).username()).isEqualTo("alice");
  }

  @Test
  @DisplayName("listUsers searches by username when query provided")
  void listUsers_searches_whenQueryProvided() {
    var tenant = tenant("bob", true);
    when(tenantRepository.findAllByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(
            eq("bob"), any()))
        .thenReturn(new PageImpl<>(List.of(tenant)));

    var page = adminUserService.listUsers(PageRequest.of(0, 10), " bob ");

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getContent().get(0).username()).isEqualTo("bob");
  }

  @Test
  @DisplayName("getUser returns detail with platform admin flags")
  void getUser_returnsDetail() {
    var tenant = tenant("alice", true);
    tenant.setDisplayName("Alice");
    when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
    when(platformAdminService.isPlatformAdmin("alice")).thenReturn(true);
    when(platformAdminService.isBootstrapAdminUsername("alice")).thenReturn(false);

    var detail = adminUserService.getUser(tenant.getId());

    assertThat(detail.username()).isEqualTo("alice");
    assertThat(detail.displayName()).isEqualTo("Alice");
    assertThat(detail.platformAdmin()).isTrue();
  }

  @Test
  @DisplayName("getUser throws when user not found")
  void getUser_throws_whenMissing() {
    var id = UUID.randomUUID();
    when(tenantRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminUserService.getUser(id))
        .isInstanceOf(ItemNotFoundException.class)
        .hasMessageContaining("userNotExist");
  }

  private static Tenant tenant(final String username, final boolean enabled) {

    final var tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername(username);
    tenant.setEmail(username + "@example.com");
    tenant.setEnabled(enabled);
    tenant.setCreatedAt(Instant.now());
    tenant.setUpdatedAt(Instant.now());
    return tenant;
  }
}
