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
package com.nuricanozturk.originhub.shared.repo.services;

import com.nuricanozturk.originhub.shared.cache.CacheNames;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.repo.dtos.RepoInfo;
import com.nuricanozturk.originhub.shared.repo.mappers.RepoMapper;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
class RepoInfoCache {

  private final RepoRepository repoRepository;
  private final RepoMapper repoMapper;

  @Cacheable(cacheNames = CacheNames.REPO_META, key = "#owner + ':' + #repoName")
  public RepoInfo fetch(final String owner, final String repoName) {
    return this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .map(this.repoMapper::toDto)
        .orElseThrow(() -> new ItemNotFoundException(RepoService.ERR_REPO_NOT_FOUND));
  }

  @CacheEvict(cacheNames = CacheNames.REPO_META, key = "#owner + ':' + #repoName")
  public void evict(final String owner, final String repoName) {}
}
