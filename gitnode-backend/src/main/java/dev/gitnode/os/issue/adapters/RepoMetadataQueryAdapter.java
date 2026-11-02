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

package dev.gitnode.os.issue.adapters;

import dev.gitnode.os.issue.api.RepoMetadataQueryPort;
import dev.gitnode.os.issue.repositories.IssueLabelRepository;
import dev.gitnode.os.issue.repositories.IssueMilestoneRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@NullMarked
public class RepoMetadataQueryAdapter implements RepoMetadataQueryPort {

  private final IssueLabelRepository labels;
  private final IssueMilestoneRepository milestones;

  @Override
  @Transactional(readOnly = true)
  public List<LabelData> findLabels(final UUID repoId, final Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return this.labels.findAllByRepoIdOrderByNameAsc(repoId).stream()
        .filter(label -> ids.contains(label.getId()))
        .map(
            label ->
                new LabelData(
                    label.getId(), label.getName(), label.getColor(), label.getDescription()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MilestoneData> findMilestone(final UUID repoId, final UUID id) {
    return this.milestones
        .findByIdAndRepoId(id, repoId)
        .map(m -> new MilestoneData(m.getId(), m.getTitle(), m.getStatus()));
  }
}
