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
package dev.gitnode.os.tree.services;

import static dev.gitnode.os.tree.utils.LanguageExtensionUtils.detectLanguage;

import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.tree.dtos.LanguageStats;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.NullMarked;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class LanguageService {

  private static final String PLAINTEXT = "plaintext";

  private final GitProvider gitProvider;

  @Cacheable(cacheNames = CacheNames.LANGUAGES, key = "#owner + ':' + #repoName + ':' + #branch")
  public List<LanguageStats> detectLanguages(
      final String owner, final String repoName, final String branch) throws IOException {

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      final var ref = gitRepo.findRef(Constants.R_HEADS + branch);

      if (ref == null) {
        throw new ItemNotFoundException("branchNotFound: " + branch);
      }

      try (final var revWalk = new RevWalk(gitRepo);
          final var treeWalk = new TreeWalk(gitRepo)) {

        final var headCommit = revWalk.parseCommit(ref.getObjectId());
        treeWalk.addTree(headCommit.getTree());
        treeWalk.setRecursive(true);

        final var langBytes = new HashMap<String, Long>();
        this.aggregateLanguages(treeWalk, gitRepo, langBytes);

        return this.toSortedStats(langBytes);
      }
    }
  }

  private void aggregateLanguages(
      final TreeWalk treeWalk, final Repository gitRepo, final Map<String, Long> langBytes)
      throws IOException {

    while (treeWalk.next()) {
      if (treeWalk.getFileMode(0) == FileMode.TREE) {
        continue;
      }

      final var name = treeWalk.getNameString();
      final var language = detectLanguage(name);

      if (PLAINTEXT.equals(language)) {
        continue;
      }

      final var size = gitRepo.open(treeWalk.getObjectId(0)).getSize();
      langBytes.merge(language, size, Long::sum);
    }
  }

  private List<LanguageStats> toSortedStats(final Map<String, Long> langBytes) {

    final long totalBytes = langBytes.values().stream().mapToLong(Long::longValue).sum();

    if (totalBytes == 0L) {
      return List.of();
    }

    return langBytes.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .map(
            e -> {
              final double pct = (double) e.getValue() / totalBytes * 100.0;
              return new LanguageStats(e.getKey(), e.getValue(), pct);
            })
        .toList();
  }
}
