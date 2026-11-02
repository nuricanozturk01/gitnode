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

package dev.gitnode.os.tag.repositories;

import dev.gitnode.os.tag.entities.ReleaseAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReleaseAssetRepository extends JpaRepository<ReleaseAsset, UUID> {

  List<ReleaseAsset> findAllByReleaseIdOrderByNameAsc(UUID releaseId);

  Optional<ReleaseAsset> findByIdAndReleaseId(UUID id, UUID releaseId);

  long countByReleaseId(UUID releaseId);

  @Query(
      "SELECT COUNT(a) > 0 FROM ReleaseAsset a WHERE a.releaseId = :releaseId AND LOWER(a.name) = LOWER(:name)")
  boolean existsByName(@Param("releaseId") UUID releaseId, @Param("name") String name);

  /** Bytes stored for all releases of the repository that owns {@code releaseId}. */
  @Query(
      "SELECT COALESCE(SUM(a.sizeBytes), 0) FROM ReleaseAsset a WHERE a.releaseId IN "
          + "(SELECT r2.id FROM Release r2 WHERE r2.repo.id = "
          + "(SELECT r.repo.id FROM Release r WHERE r.id = :releaseId))")
  long sumSizeInRepositoryOf(@Param("releaseId") UUID releaseId);

  @Modifying
  @Query("UPDATE ReleaseAsset a SET a.downloadCount = a.downloadCount + 1 WHERE a.id = :id")
  void incrementDownloads(@Param("id") UUID id);
}
