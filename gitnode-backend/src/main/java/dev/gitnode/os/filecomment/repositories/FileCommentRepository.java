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

package dev.gitnode.os.filecomment.repositories;

import dev.gitnode.os.filecomment.entities.FileComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FileCommentRepository extends JpaRepository<FileComment, UUID> {

  @Query(
      "SELECT c FROM FileComment c JOIN FETCH c.author"
          + " WHERE c.repoId = :repoId AND c.path = :path ORDER BY c.createdAt ASC")
  List<FileComment> findForPath(UUID repoId, String path, Pageable pageable);

  @Query(
      "SELECT DISTINCT c.author.id FROM FileComment c WHERE c.id = :rootId OR c.parentId = :rootId")
  List<UUID> findThreadParticipantIds(UUID rootId);

  @Query("SELECT c FROM FileComment c JOIN FETCH c.author WHERE c.id = :id AND c.repoId = :repoId")
  java.util.Optional<FileComment> findInRepo(UUID id, UUID repoId);
}
