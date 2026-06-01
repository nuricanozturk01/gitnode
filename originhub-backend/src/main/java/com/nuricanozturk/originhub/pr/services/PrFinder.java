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
package com.nuricanozturk.originhub.pr.services;

import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class PrFinder {

  private final RepoRepository repoRepository;
  private final PrRepository prRepository;
  private final TenantRepository tenantRepository;

  Repo findRepo(final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
  }

  PullRequest findPr(final UUID repoId, final int number) {
    return this.prRepository
        .findByRepoIdAndNumber(repoId, number)
        .orElseThrow(() -> new ItemNotFoundException("Pull request not found: #" + number));
  }

  Tenant findTenant(final UUID id) {
    return this.tenantRepository
        .findById(id)
        .orElseThrow(() -> new ItemNotFoundException("User not found"));
  }
}
