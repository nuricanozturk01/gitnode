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
package com.nuricanozturk.originhub.http.services;

import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HttpGitAccessValidator {

  private final @NonNull RepoRepository repoRepository;

  public void assertAccess(
      final @NonNull Tenant tenant,
      final @NonNull String owner,
      final @NonNull String repoName,
      final boolean isWrite) {

    final var repo = this.repoRepository.findByOwnerUsernameAndName(owner, repoName)
        .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + owner + "/" + repoName));

    final var isOwner = repo.getOwner().getId().equals(tenant.getId());

    if (!isOwner) {
      log.warn(
          "Access denied: user={}, repo={}/{}, write={}",
          tenant.getUsername(),
          owner,
          repoName,
          isWrite);
      throw new IllegalArgumentException("Access denied");
    }

    log.debug(
        "Access granted: user={}, repo={}/{}, write={}",
        tenant.getUsername(),
        owner,
        repoName,
        isWrite);
  }
}
