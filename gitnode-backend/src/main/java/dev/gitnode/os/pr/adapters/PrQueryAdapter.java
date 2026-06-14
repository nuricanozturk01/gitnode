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
package dev.gitnode.os.pr.adapters;

import dev.gitnode.os.pr.api.PrData;
import dev.gitnode.os.pr.api.PrQueryPort;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.pr.repositories.PrRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Pageable;
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
  public Optional<PrData> findByRepoIdAndNumber(final UUID repoId, final int number) {
    return this.prRepository.findByRepoIdAndNumber(repoId, number).map(this::toData);
  }

  @Override
  public List<PrData> findOpenByRepoId(final UUID repoId) {
    return this.prRepository
        .findAllByRepoIdAndStatusOrderByCreatedAtDesc(repoId, "OPEN", Pageable.unpaged())
        .stream()
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
        pr.getStatus(),
        pr.getSourceSha(),
        pr.getAuthor().getId());
  }
}
