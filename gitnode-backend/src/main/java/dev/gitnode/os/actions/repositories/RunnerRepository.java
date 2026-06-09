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
package dev.gitnode.os.actions.repositories;

import dev.gitnode.os.actions.entities.Runner;
import dev.gitnode.os.actions.entities.RunnerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface RunnerRepository extends JpaRepository<Runner, UUID> {

  List<Runner> findAllByTenantId(UUID tenantId);

  List<Runner> findAllByStatus(RunnerStatus status);

  long countByStatus(RunnerStatus status);

  @Modifying
  @Query(
      "UPDATE Runner r SET r.status = 'OFFLINE' WHERE r.lastHeartbeat < :threshold"
          + " AND r.status != 'OFFLINE'")
  int markStaleRunnersOffline(@Param("threshold") Instant threshold);
}
