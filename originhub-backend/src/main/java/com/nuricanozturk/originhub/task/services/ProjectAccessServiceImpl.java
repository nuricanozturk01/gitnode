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
package com.nuricanozturk.originhub.task.services;

import com.nuricanozturk.originhub.shared.project.ProjectAccessService;
import com.nuricanozturk.originhub.shared.project.ProjectSummary;
import com.nuricanozturk.originhub.task.repositories.ProjectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@NullMarked
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ProjectAccessServiceImpl implements ProjectAccessService {

  private final ProjectRepository projectRepository;

  @Override
  public Optional<ProjectSummary> findByOwnerAndCode(
      final String ownerUsername, final String codePrefix) {
    return this.projectRepository
        .findByOwnerUsernameAndCodePrefix(ownerUsername, codePrefix)
        .map(p -> new ProjectSummary(p.getId(), p.getOwner().getUsername()));
  }
}
