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

package dev.gitnode.os.auth.repositories;

import dev.gitnode.os.auth.entities.RefreshToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /**
   * Atomically claims an unused, unrevoked token; returns 0 when it was already used or revoked.
   */
  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.usedAt = :now"
          + " WHERE t.id = :id AND t.usedAt IS NULL AND t.revokedAt IS NULL AND t.expiresAt > :now")
  int claim(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now"
          + " WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  @Modifying
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now"
          + " WHERE t.userId = :userId AND t.revokedAt IS NULL")
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
  int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
