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
package com.nuricanozturk.originhub.shared.tenant.repositories;

import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  @Query("from Tenant t where t.username = :emailOrUsername or t.email = :emailOrUsername")
  Optional<Tenant> findByUsernameOrEmail(String emailOrUsername);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  Optional<Tenant> findByPasswordRecoveryCode(@NotNull String recoveryCode);

  @Modifying
  @Query("update Tenant t set t.passwordRecoveryCode = :recoveryCode where t.id = :id")
  void updatePasswordRecoveryCode(UUID id, String recoveryCode);

  Optional<Tenant> findByUsername(String username);

  List<Tenant> findTop10ByUsernameIgnoreCaseStartingWithOrderByUsernameAsc(String prefix);

  @Query("from Tenant t where t.email in :emails")
  List<Tenant> findAllByEmailIn(Collection<String> emails);

  Page<Tenant> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Page<Tenant> findAllByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(
      String username, Pageable pageable);

  long countByEnabledTrue();

  long countByCreatedAtAfter(Instant since);

  List<Tenant> findAllByCreatedAtAfter(Instant since);
}
