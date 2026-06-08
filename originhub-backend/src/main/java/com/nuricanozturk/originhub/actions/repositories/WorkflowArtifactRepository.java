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
package com.nuricanozturk.originhub.actions.repositories;

import com.nuricanozturk.originhub.actions.entities.WorkflowArtifact;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowArtifactRepository extends JpaRepository<WorkflowArtifact, UUID> {

  Page<WorkflowArtifact> findAllByRunIdOrderByCreatedAtDesc(UUID runId, Pageable pageable);

  Optional<WorkflowArtifact> findByRunIdAndName(UUID runId, String name);

  List<WorkflowArtifact> findAllByExpiresAtBefore(Instant threshold, Pageable pageable);
}
