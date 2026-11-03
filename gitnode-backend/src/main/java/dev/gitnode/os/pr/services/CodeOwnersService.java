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
package dev.gitnode.os.pr.services;

import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.pr.entities.PullRequest;
import dev.gitnode.os.repo.api.CollaboratorAccessPort;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * Resolves who must review a pull request according to the CODEOWNERS file of its target branch.
 * The file is read from the target branch, never from the source, so a pull request cannot grant
 * itself ownership.
 *
 * <p>Deliberately not transactional: callers may swallow its failures without poisoning their own
 * transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class CodeOwnersService {

  private static final int MAX_FILE_BYTES = 1024 * 1024;

  private final GitProvider git;
  private final TenantQueryPort tenants;
  private final CollaboratorAccessPort access;
  private final PrReviewService reviews;

  /** A CODEOWNERS line that applies to the pull request, with the owners who can approve it. */
  record Requirement(String pattern, List<String> owners, Set<UUID> ownerIds) {}

  /**
   * Lines of the target branch's CODEOWNERS that own at least one file this pull request changes.
   */
  List<Requirement> requirements(
      final String owner, final String repoName, final RepoRef repo, final PullRequest pr)
      throws IOException {
    try (final var gitRepo = this.git.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {
      final var sourceRef = gitRepo.exactRef(Constants.R_HEADS + pr.getSourceBranch());
      final var targetRef = gitRepo.exactRef(Constants.R_HEADS + pr.getTargetBranch());
      if (sourceRef == null || targetRef == null) {
        return List.of();
      }
      final var target = walk.parseCommit(targetRef.getObjectId());
      final var file = this.readFile(gitRepo, target);
      if (file.isEmpty()) {
        return List.of();
      }
      final var source = walk.parseCommit(sourceRef.getObjectId());
      final var matched = new LinkedHashSet<CodeOwnersFile.Rule>();
      for (final var path : this.changedPaths(gitRepo, source, target)) {
        file.ruleFor(path).filter(rule -> !rule.owners().isEmpty()).ifPresent(matched::add);
      }
      final var result = new ArrayList<Requirement>();
      for (final var rule : matched) {
        this.resolve(repo, pr, rule).ifPresent(result::add);
      }
      return result;
    }
  }

  /** Accounts that should be asked to review: every eligible owner of a changed file. */
  Set<UUID> ownersToRequest(
      final String owner, final String repoName, final RepoRef repo, final PullRequest pr)
      throws IOException {
    final var ids = new HashSet<UUID>();
    this.requirements(owner, repoName, repo, pr).forEach(req -> ids.addAll(req.ownerIds()));
    return ids;
  }

  /** The requirements no code owner has approved yet for the current heads. */
  List<Requirement> unmet(
      final String owner,
      final String repoName,
      final RepoRef repo,
      final PullRequest pr,
      final PrReviewService.BranchHeads heads)
      throws IOException {
    final var required = this.requirements(owner, repoName, repo, pr);
    if (required.isEmpty()) {
      return required;
    }
    final var approvers = this.reviews.currentApproverIds(repo, pr, heads);
    return required.stream()
        .filter(req -> Collections.disjoint(req.ownerIds(), approvers))
        .toList();
  }

  private java.util.Optional<Requirement> resolve(
      final RepoRef repo, final PullRequest pr, final CodeOwnersFile.Rule rule) {
    final var names = new ArrayList<String>();
    final var ids = new HashSet<UUID>();
    for (final var username : rule.owners()) {
      this.tenants
          .findByUsernameIgnoreCase(username)
          .filter(TenantQueryPort.TenantRef::enabled)
          .filter(user -> !user.id().equals(pr.getAuthorId()))
          .filter(user -> this.canReview(repo, user.id()))
          .ifPresent(
              user -> {
                names.add(user.username());
                ids.add(user.id());
              });
    }
    // Owners nobody can act on (unknown, disabled, only the author) must not block merging forever.
    return ids.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(
            new Requirement(rule.pattern(), List.copyOf(names), Set.copyOf(ids)));
  }

  private boolean canReview(final RepoRef repo, final UUID userId) {
    return repo.ownerId().equals(userId)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_REVIEW)
        || this.access.hasPermission(repo.id(), userId, CollaboratorPermission.PULL_REQUEST_MERGE);
  }

  private CodeOwnersFile readFile(final Repository gitRepo, final RevCommit target)
      throws IOException {
    for (final var location : CodeOwnersFile.LOCATIONS) {
      try (final var tree = TreeWalk.forPath(gitRepo, location, target.getTree())) {
        if (tree == null || FileMode.TREE.equals(tree.getFileMode(0))) {
          continue;
        }
        try {
          final var bytes = gitRepo.open(tree.getObjectId(0)).getCachedBytes(MAX_FILE_BYTES);
          return CodeOwnersFile.parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (final LargeObjectException tooLarge) {
          log.warn("Ignoring oversized {} on {}", location, target.getName());
          return CodeOwnersFile.empty();
        }
      }
    }
    return CodeOwnersFile.empty();
  }

  private Set<String> changedPaths(
      final Repository gitRepo, final RevCommit source, final RevCommit target) throws IOException {
    final RevCommit base;
    try (final var walk = new RevWalk(gitRepo)) {
      walk.setRevFilter(RevFilter.MERGE_BASE);
      walk.markStart(walk.parseCommit(source.getId()));
      walk.markStart(walk.parseCommit(target.getId()));
      base = walk.next();
    }
    final var paths = new HashSet<String>();
    try (final var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        final var reader = gitRepo.newObjectReader()) {
      formatter.setRepository(gitRepo);
      formatter.setDetectRenames(true);
      final var oldTree = new CanonicalTreeParser();
      if (base != null) {
        oldTree.reset(reader, base.getTree());
      }
      final var newTree = new CanonicalTreeParser();
      newTree.reset(reader, source.getTree());
      for (final var entry : formatter.scan(oldTree, newTree)) {
        if (entry.getChangeType() != DiffEntry.ChangeType.ADD) {
          paths.add(entry.getOldPath());
        }
        if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
          paths.add(entry.getNewPath());
        }
      }
    }
    return paths;
  }
}
