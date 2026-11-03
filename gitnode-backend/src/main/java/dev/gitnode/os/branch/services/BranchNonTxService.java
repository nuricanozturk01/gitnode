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
package dev.gitnode.os.branch.services;

import static java.util.Comparator.comparing;

import dev.gitnode.os.branch.api.BranchForm;
import dev.gitnode.os.branch.api.BranchInfo;
import dev.gitnode.os.branch.api.BranchProtocolService;
import dev.gitnode.os.events.branch.BranchCreatedEvent;
import dev.gitnode.os.events.branch.BranchDeletedEvent;
import dev.gitnode.os.shared.cache.CacheNames;
import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@NullMarked
public class BranchNonTxService implements BranchProtocolService {

  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;

  private final BranchTxService branchTxService;
  private final GitProvider gitProvider;
  private final ApplicationEventPublisher eventPublisher;
  private final BranchProtectionService branchProtection;

  @Override
  @CacheEvict(cacheNames = CacheNames.BRANCHES, key = "#owner + ':' + #repoName")
  public BranchInfo create(final String owner, final String repoName, final BranchForm form)
      throws IOException {

    // source -> branch (main -> delete_feature_branch)
    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      this.checkBranchExists(gitRepo, form.getName());

      final var result = this.createNewBranch(gitRepo, form.getName(), form.getSourceBranch());

      this.checkCreateResult(result);

      final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);
      this.eventPublisher.publishEvent(new BranchCreatedEvent(repo.id(), form.getName()));

      return this.get(owner, repoName, form.getName());
    }
  }

  @Override
  @CacheEvict(cacheNames = CacheNames.BRANCHES, key = "#owner + ':' + #repoName")
  public void delete(final String owner, final String repoName, final String branchName)
      throws IOException {

    this.branchProtection.assertDirectWriteAllowed(owner, repoName, branchName);

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    this.checkDefaultBranch(branchName, repo.defaultBranch());

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkBranchNonExists(gitRepo, branchName);

      final var result = this.deleteBranch(gitRepo, branchName);

      this.checkDeleteResult(result);
    }

    this.eventPublisher.publishEvent(new BranchDeletedEvent(repo.id(), branchName));
  }

  @Override
  @CacheEvict(cacheNames = CacheNames.BRANCHES, key = "#owner + ':' + #repoName")
  public void setDefaultBranch(final String owner, final String repoName, final String branchName)
      throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkBranchNonExists(gitRepo, branchName);

      final var result = this.updateDefaultBranch(gitRepo, branchName);

      this.checkLinkResult(result);
    }

    this.branchTxService.updateDefaultBranch(repo.id(), branchName);
  }

  @Override
  @Cacheable(cacheNames = CacheNames.BRANCHES, key = "#owner + ':' + #repoName")
  public List<BranchInfo> getAll(final String owner, final String repoName) throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var refs = gitRepo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS);

      if (refs.isEmpty()) {
        return this.unbornBranchList(gitRepo, repo.defaultBranch());
      }

      final var comparator =
          comparing((BranchInfo b) -> b.isDefault() ? 0 : 1).thenComparing(BranchInfo::name);

      final var defaultRef = gitRepo.exactRef(Constants.R_HEADS + repo.defaultBranch());
      return refs.stream()
          .map(ref -> this.buildBranchInfo(walk, ref, repo.defaultBranch()))
          .map(info -> this.withDivergence(gitRepo, info, defaultRef))
          .sorted(comparator)
          .toList();
    }
  }

  @Override
  public BranchInfo get(final String owner, final String repoName, final String branchName)
      throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var ref = this.getBranchRef(gitRepo, branchName);

      return this.buildBranchInfo(walk, ref, repo.defaultBranch());
    }
  }

  private List<BranchInfo> unbornBranchList(final Repository gitRepo, final String defaultBranch)
      throws IOException {

    final var head = gitRepo.getRefDatabase().findRef(Constants.HEAD);

    if (head == null || !head.isSymbolic()) {
      return List.of();
    }

    final var branchName = head.getTarget().getName().replace(Constants.R_HEADS, "");

    return List.of(
        BranchInfo.builder()
            .name(branchName)
            .lastCommitSha("")
            .lastCommitShortSha("")
            .lastCommitMessage("")
            .lastCommitAuthor("")
            .lastCommitDate(Instant.EPOCH)
            .isDefault(branchName.equals(defaultBranch))
            .build());
  }

  private RefUpdate.Result createNewBranch(
      final Repository gitRepo, final String newBranchName, final String sourceBranch)
      throws IOException {

    // source sha
    final var sourceObjectId = this.getSourceObjectId(gitRepo, sourceBranch);

    final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + newBranchName);
    refUpdate.setNewObjectId(sourceObjectId);
    refUpdate.setRefLogMessage("branch: Created from " + sourceBranch, false);

    return refUpdate.update();
  }

  private RefUpdate.Result deleteBranch(final Repository gitRepo, final String branchName)
      throws IOException {

    final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + branchName);
    refUpdate.setForceUpdate(true); // Delete non merged branch

    return refUpdate.delete();
  }

  private RefUpdate.Result updateDefaultBranch(final Repository gitRepo, final String branchName)
      throws IOException {

    final var headUpdate = gitRepo.updateRef(Constants.HEAD, true);

    return headUpdate.link(Constants.R_HEADS + branchName);
  }

  private ObjectId getSourceObjectId(final Repository gitRepo, final String sourceBranch)
      throws IOException {

    final var sourceObjectId = gitRepo.resolve(sourceBranch);

    if (sourceObjectId == null) {
      throw new ItemNotFoundException("sourceNotFound: " + sourceBranch);
    }

    return sourceObjectId;
  }

  private Ref getBranchRef(final Repository gitRepo, final String name) throws IOException {

    final var ref = gitRepo.findRef(Constants.R_HEADS + name);

    if (ref == null) {
      throw new ItemNotFoundException("branchNotFound: " + name);
    }

    return ref;
  }

  private void checkBranchExists(final Repository gitRepo, final String name) throws IOException {

    if (gitRepo.findRef(Constants.R_HEADS + name) != null) {
      throw new ItemAlreadyExistsException("branchAlreadyExists: " + name);
    }
  }

  private void checkBranchNonExists(final Repository gitRepo, final String name)
      throws IOException {

    if (gitRepo.findRef(Constants.R_HEADS + name) == null) {
      throw new ItemNotFoundException("branchNotFound: " + name);
    }
  }

  private void checkCreateResult(final RefUpdate.Result result) {

    if (result != RefUpdate.Result.NEW) {
      throw new RuntimeException("Failed to create branch: " + result);
    }
  }

  private void checkDeleteResult(final RefUpdate.Result result) {
    if (result != RefUpdate.Result.FORCED) {
      throw new RuntimeException("Failed to delete branch: " + result);
    }
  }

  private void checkLinkResult(final RefUpdate.Result result) {

    if (result != RefUpdate.Result.NEW
        && result != RefUpdate.Result.FORCED
        && result != RefUpdate.Result.NO_CHANGE) {
      throw new RuntimeException("Failed to set default branch: " + result);
    }
  }

  private void checkDefaultBranch(final String branchName, final String defaultBranch) {

    if (branchName.equals(defaultBranch)) {
      throw new ErrorOccurredException("defaultBranchCannotDelete: " + branchName);
    }
  }

  /** Commits counted per side stop at this many, shown as "500+" by the UI. */
  static final int MAX_DIVERGENCE = 500;

  private BranchInfo withDivergence(
      final Repository gitRepo, final BranchInfo info, final @Nullable Ref defaultRef) {
    if (info.isDefault() || defaultRef == null || defaultRef.getObjectId() == null) {
      return info;
    }
    try {
      final var branch = ObjectId.fromString(info.lastCommitSha());
      return info.toBuilder()
          .ahead(this.countExclusive(gitRepo, branch, defaultRef.getObjectId()))
          .behind(this.countExclusive(gitRepo, defaultRef.getObjectId(), branch))
          .build();
    } catch (final IOException | RuntimeException ex) {
      log.debug("Could not compute divergence for {}: {}", info.name(), ex.getMessage());
      return info;
    }
  }

  /** Number of commits reachable from {@code tip} but not from {@code base}, capped. */
  private int countExclusive(final Repository gitRepo, final ObjectId tip, final ObjectId base)
      throws IOException {
    try (final var walk = new RevWalk(gitRepo)) {
      walk.markStart(walk.parseCommit(tip));
      walk.markUninteresting(walk.parseCommit(base));
      var count = 0;
      for (final var ignored : walk) {
        if (++count >= MAX_DIVERGENCE) {
          break;
        }
      }
      return count;
    }
  }

  private BranchInfo buildBranchInfo(
      final RevWalk walk, final Ref ref, final String defaultBranch) {

    try {

      final var commit = walk.parseCommit(ref.getObjectId());

      // refs/heads/abc -> abc
      final var branchName = ref.getName().replace(Constants.R_HEADS, "");
      final var commitSha = ref.getObjectId().getName();

      return BranchInfo.builder()
          .name(branchName)
          .lastCommitSha(commitSha)
          .lastCommitShortSha(commitSha.substring(0, DEFAULT_SHORT_SHA_LENGTH))
          .lastCommitMessage(commit.getShortMessage())
          .lastCommitAuthor(commit.getAuthorIdent().getName())
          .lastCommitDate(commit.getAuthorIdent().getWhenAsInstant())
          .isDefault(branchName.equals(defaultBranch))
          .build();

    } catch (final IOException ex) {
      log.warn("Failed into creating branch info: ", ex);
      throw new ErrorOccurredException(
          "Failed into creating branch info: %s".formatted(ex.getMessage()));
    }
  }
}
