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
package com.nuricanozturk.originhub.branch.services;

import static java.util.Comparator.comparing;

import com.nuricanozturk.originhub.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.branch.dtos.BranchInfo;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ErrorOccurredException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import com.nuricanozturk.originhub.shared.errorhandling.exceptions.ItemNotFoundException;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
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
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchNonTxService {

  private static final int DEFAULT_SHORT_SHA_LENGTH = 7;

  private final @NonNull BranchTxService branchTxService;
  private final @NonNull GitProvider gitProvider;

  public @NonNull BranchInfo create(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull BranchForm form)
      throws IOException {

    // source -> branch (main -> delete_feature_branch)
    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {
      this.checkBranchExists(gitRepo, form.getName());

      final var result = this.createNewBranch(gitRepo, form.getName(), form.getSourceBranch());

      this.checkCreateResult(result);

      return this.get(owner, repoName, form.getName());
    }
  }

  public void delete(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String branchName)
      throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    this.checkDefaultBranch(branchName, repo.getDefaultBranch());

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkBranchNonExists(gitRepo, branchName);

      final var result = this.deleteBranch(gitRepo, branchName);

      this.checkDeleteResult(result);
    }
  }

  public void setDefaultBranch(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String branchName)
      throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName)) {

      this.checkBranchNonExists(gitRepo, branchName);

      final var result = this.updateDefaultBranch(gitRepo, branchName);

      this.checkLinkResult(result);
    }

    this.branchTxService.updateDefaultBranch(repo.getId(), branchName);
  }

  public @NonNull List<BranchInfo> getAll(
      final @NonNull String owner, final @NonNull String repoName) throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var refs = gitRepo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS);

      if (refs.isEmpty()) {
        return this.unbornBranchList(gitRepo, repo.getDefaultBranch());
      }

      final var comparator =
          comparing((BranchInfo b) -> b.isDefault() ? 0 : 1).thenComparing(BranchInfo::name);

      return refs.stream()
          .map(ref -> this.buildBranchInfo(walk, ref, repo.getDefaultBranch()))
          .sorted(comparator)
          .toList();
    }
  }

  private @NonNull List<BranchInfo> unbornBranchList(
      final @NonNull Repository gitRepo, final @NonNull String defaultBranch) throws IOException {

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

  public @NonNull BranchInfo get(
      final @NonNull String owner, final @NonNull String repoName, final @NonNull String branchName)
      throws IOException {

    final var repo = this.branchTxService.findRepoByOwnerAndRepoName(owner, repoName);

    try (final var gitRepo = this.gitProvider.open(owner, repoName);
        final var walk = new RevWalk(gitRepo)) {

      final var ref = this.getBranchRef(gitRepo, branchName);

      return this.buildBranchInfo(walk, ref, repo.getDefaultBranch());
    }
  }

  private RefUpdate.Result createNewBranch(
      final @NonNull Repository gitRepo,
      final @NonNull String newBranchName,
      final @NonNull String sourceBranch)
      throws IOException {

    // source sha
    final var sourceObjectId = this.getSourceObjectId(gitRepo, sourceBranch);

    final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + newBranchName);
    refUpdate.setNewObjectId(sourceObjectId);
    refUpdate.setRefLogMessage("branch: Created from " + sourceBranch, false);

    return refUpdate.update();
  }

  private RefUpdate.Result deleteBranch(
      final @NonNull Repository gitRepo, final @NonNull String branchName) throws IOException {

    final var refUpdate = gitRepo.updateRef(Constants.R_HEADS + branchName);
    refUpdate.setForceUpdate(true); // Delete non merged branch

    return refUpdate.delete();
  }

  private RefUpdate.Result updateDefaultBranch(
      final @NonNull Repository gitRepo, final @NonNull String branchName) throws IOException {

    final var headUpdate = gitRepo.updateRef(Constants.HEAD, true);

    return headUpdate.link(Constants.R_HEADS + branchName);
  }

  private @NonNull ObjectId getSourceObjectId(
      final @NonNull Repository gitRepo, final @NonNull String sourceBranch) throws IOException {

    final var sourceObjectId = gitRepo.resolve(sourceBranch);

    if (sourceObjectId == null) {
      throw new ItemNotFoundException("sourceNotFound: " + sourceBranch);
    }

    return sourceObjectId;
  }

  private @NonNull Ref getBranchRef(final @NonNull Repository gitRepo, final @NonNull String name)
      throws IOException {

    final var ref = gitRepo.findRef(Constants.R_HEADS + name);

    if (ref == null) {
      throw new ItemNotFoundException("branchNotFound: " + name);
    }

    return ref;
  }

  private void checkBranchExists(final @NonNull Repository gitRepo, final @NonNull String name)
      throws IOException {

    if (gitRepo.findRef(Constants.R_HEADS + name) != null) {
      throw new ItemAlreadyExistsException("branchAlreadyExists: " + name);
    }
  }

  private void checkBranchNonExists(final @NonNull Repository gitRepo, final @NonNull String name)
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

  private void checkDefaultBranch(
      final @NonNull String branchName, final @NonNull String defaultBranch) {

    if (branchName.equals(defaultBranch)) {
      throw new ErrorOccurredException("defaultBranchCannotDelete: " + branchName);
    }
  }

  private @NonNull BranchInfo buildBranchInfo(
      final @NonNull RevWalk walk, final @NonNull Ref ref, final @NonNull String defaultBranch) {

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
