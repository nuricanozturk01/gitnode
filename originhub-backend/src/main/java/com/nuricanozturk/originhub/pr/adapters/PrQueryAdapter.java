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
package com.nuricanozturk.originhub.pr.adapters;

import com.nuricanozturk.originhub.pr.api.PrData;
import com.nuricanozturk.originhub.pr.api.PrQueryPort;
import com.nuricanozturk.originhub.pr.entities.PullRequest;
import com.nuricanozturk.originhub.pr.repositories.PrRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@NullMarked
public class PrQueryAdapter implements PrQueryPort {

  private final PrRepository prRepository;

  @Override
  public Optional<PrData> findById(final UUID prId) {
    return this.prRepository.findById(prId).map(this::toData);
  }

  @Override
  public List<PrData> findOpenByRepoId(final UUID repoId) {
    return this.prRepository.findAllByRepoIdAndStatusOrderByCreatedAtDesc(repoId, "OPEN").stream()
        .map(this::toData)
        .toList();
  }

  private PrData toData(final PullRequest pr) {
    return new PrData(
        pr.getId(),
        pr.getNumber(),
        pr.getTitle(),
        pr.getSourceBranch(),
        pr.getTargetBranch(),
        pr.getStatus());
  }
}
