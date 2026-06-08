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
package dev.gitnode.os.actions.services;

import dev.gitnode.os.actions.dtos.request.RunnerGroupRequest;
import dev.gitnode.os.actions.dtos.response.RunnerGroupResponse;
import dev.gitnode.os.actions.entities.RunnerGroup;
import dev.gitnode.os.actions.repositories.RunnerGroupRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NullMarked
@Transactional
public class RunnerGroupService {

  private final RunnerGroupRepository groupRepository;

  public RunnerGroupResponse create(final Long orgId, final RunnerGroupRequest req) {

    if (this.groupRepository.existsByOrgIdAndName(orgId, req.name())) {
      throw new ErrorOccurredException("Runner group already exists: " + req.name());
    }

    final var group = new RunnerGroup();
    group.setOrgId(orgId);
    group.setName(req.name());
    group.setLabels(req.labels() != null ? req.labels() : List.of());

    final var saved = this.groupRepository.save(group);
    return this.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<RunnerGroupResponse> listByOrg(final Long orgId, final Pageable pageable) {

    return this.groupRepository.findAllByOrgId(orgId, pageable).map(this::toResponse);
  }

  public void delete(final Long groupId, final Long orgId) {

    final var group =
        this.groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ItemNotFoundException("Runner group not found: " + groupId));

    if (!group.getOrgId().equals(orgId)) {
      throw new AccessNotAllowedException("Runner group does not belong to this org");
    }

    this.groupRepository.delete(group);
  }

  private RunnerGroupResponse toResponse(final RunnerGroup g) {

    return new RunnerGroupResponse(
        g.getId(), g.getOrgId(), g.getName(), g.getLabels(), g.getCreatedAt());
  }
}
