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
package dev.gitnode.os.shared.cache;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@NullMarked
public class RepoCacheInvalidator {

  private final CachePatternEvictor evictor;

  /** Evicts branch-scoped caches (tree, blob, languages, commits) for the given branch. */
  public void evictBranchScoped(final String owner, final String repo, final String branch) {
    final var prefix = owner + ":" + repo + ":" + branch;
    this.evictor.evict(CacheNames.TREE, prefix + "*");
    this.evictor.evict(CacheNames.BLOB, prefix + "*");
    this.evictor.evict(CacheNames.LANGUAGES, owner + ":" + repo + ":" + branch);
    this.evictor.evict(CacheNames.COMMITS, prefix + "*");
  }

  /** Evicts the branch list cache for a repo. */
  public void evictBranches(final String owner, final String repo) {
    this.evictor.evict(CacheNames.BRANCHES, owner + ":" + repo);
  }

  /** Evicts the tag list cache for a repo. */
  public void evictTags(final String owner, final String repo) {
    this.evictor.evict(CacheNames.TAGS, owner + ":" + repo);
  }
}
