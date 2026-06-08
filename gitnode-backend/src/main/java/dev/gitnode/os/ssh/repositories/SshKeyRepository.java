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
package dev.gitnode.os.ssh.repositories;

import dev.gitnode.os.ssh.entities.SshKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface SshKeyRepository extends JpaRepository<SshKey, UUID> {

  List<SshKey> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<SshKey> findByFingerprint(String fingerprint);

  boolean existsByTenantIdAndTitle(UUID tenantId, String title);

  void deleteByIdAndTenantId(UUID id, UUID tenantId);

  @Query("SELECT s FROM SshKey s JOIN FETCH s.tenant WHERE s.fingerprint = :fingerprint")
  Optional<SshKey> findByFingerprintWithTenant(String fingerprint);
}
