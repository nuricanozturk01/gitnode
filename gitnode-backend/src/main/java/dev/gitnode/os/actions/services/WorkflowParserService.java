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
package dev.gitnode.os.actions.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.gitnode.os.actions.model.WorkflowModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class WorkflowParserService {

  static final String WORKFLOW_DIR = ".gitnode/workflows";
  static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Value("${gitnode.git.repo-root}")
  private String repoRoot;

  public List<ParsedWorkflow> parseWorkflows(
      final String ownerUsername, final String repoName, final String branchRef) {
    return this.scan(ownerUsername, repoName, branchRef, new ArrayList<>());
  }

  /** Workflow files at {@code branchRef} that exist but cannot be parsed, with the reason. */
  public List<ParseFailure> findParseFailures(
      final String ownerUsername, final String repoName, final String branchRef) {
    final var failures = new ArrayList<ParseFailure>();
    this.scan(ownerUsername, repoName, branchRef, failures);
    return List.copyOf(failures);
  }

  private List<ParsedWorkflow> scan(
      final String ownerUsername,
      final String repoName,
      final String branchRef,
      final List<ParseFailure> failures) {

    final var repoPath = Path.of(this.repoRoot, ownerUsername, repoName + ".git");
    final var result = new ArrayList<ParsedWorkflow>();

    try (Repository gitRepo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build()) {

      final var headId = gitRepo.resolve(branchRef);

      if (headId == null) {
        log.debug("Cannot resolve branch {} in {}/{}", branchRef, ownerUsername, repoName);
        return List.of();
      }

      try (RevWalk revWalk = new RevWalk(gitRepo)) {
        final var commit = revWalk.parseCommit(headId);
        final var tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(gitRepo)) {
          treeWalk.addTree(tree);
          treeWalk.setRecursive(true);
          treeWalk.setFilter(PathFilter.create(WORKFLOW_DIR));

          while (treeWalk.next()) {
            this.parseEntry(gitRepo, treeWalk, result, failures);
          }
        }
      }
    } catch (final IOException e) {
      log.warn("Failed to read workflows from {}/{}: {}", ownerUsername, repoName, e.getMessage());
    }

    return List.copyOf(result);
  }

  /** Parses one file of the workflows directory into {@code result}, or records why it failed. */
  private void parseEntry(
      final Repository gitRepo,
      final TreeWalk treeWalk,
      final List<ParsedWorkflow> result,
      final List<ParseFailure> failures)
      throws IOException {

    final var path = treeWalk.getPathString();
    if (!path.endsWith(".yml") && !path.endsWith(".yaml")) {
      return;
    }
    final var content = this.readBlob(gitRepo, treeWalk.getObjectId(0));
    if (content == null) {
      return;
    }
    try {
      result.add(new ParsedWorkflow(path, content, this.readModel(content)));
    } catch (final IOException e) {
      log.warn("Failed to parse workflow YAML at {}: {}", path, e.getMessage());
      failures.add(new ParseFailure(path, firstLine(e.getMessage())));
    }
  }

  /** The commit message of {@code rev} (a sha or ref), or null when it cannot be read. */
  public @Nullable String readCommitMessage(
      final String ownerUsername, final String repoName, final @Nullable String rev) {

    if (rev == null || rev.isBlank()) {
      return null;
    }
    final var repoPath = Path.of(this.repoRoot, ownerUsername, repoName + ".git");
    try (Repository gitRepo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build();
        RevWalk revWalk = new RevWalk(gitRepo)) {
      final var id = gitRepo.resolve(rev);
      return id == null ? null : revWalk.parseCommit(id).getFullMessage().strip();
    } catch (final IOException | RuntimeException e) {
      log.debug("Cannot read commit message of {} in {}/{}", rev, ownerUsername, repoName);
      return null;
    }
  }

  /** The commit {@code ref} (a branch ref, tag ref or sha) points to, or null when it is gone. */
  public @Nullable String resolveSha(
      final String ownerUsername, final String repoName, final String ref) {

    final var repoPath = Path.of(this.repoRoot, ownerUsername, repoName + ".git");
    try (Repository gitRepo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build()) {
      final var id = gitRepo.resolve(ref + "^{commit}");
      return id == null ? null : id.name();
    } catch (final IOException | RuntimeException e) {
      log.debug("Cannot resolve {} in {}/{}", ref, ownerUsername, repoName);
      return null;
    }
  }

  /**
   * Files a push changed: {@code before..after}. When the previous tip is unknown or a new branch
   * (null or the all-zero sha) the commit is compared with its first parent. Null means the files
   * could not be determined, and callers then do not filter by path.
   */
  public @Nullable List<String> changedFiles(
      final String ownerUsername,
      final String repoName,
      final @Nullable String before,
      final String after) {

    final var repoPath = Path.of(this.repoRoot, ownerUsername, repoName + ".git");
    try (Repository gitRepo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build();
        RevWalk revWalk = new RevWalk(gitRepo)) {
      final var afterId = gitRepo.resolve(after + "^{commit}");
      if (afterId == null) {
        return null;
      }
      final var newCommit = revWalk.parseCommit(afterId);
      RevCommit base = null;
      final var beforeId = isUnknownSha(before) ? null : gitRepo.resolve(before + "^{commit}");
      if (beforeId != null) {
        base = revWalk.parseCommit(beforeId);
      } else if (newCommit.getParentCount() > 0) {
        base = revWalk.parseCommit(newCommit.getParent(0));
      }
      return diffPaths(gitRepo, base, newCommit);
    } catch (final IOException | RuntimeException e) {
      log.warn("Cannot list changed files in {}/{}: {}", ownerUsername, repoName, e.getMessage());
      return null;
    }
  }

  /**
   * Files a pull request changes: from the merge base of the target branch and {@code headSha} to
   * the head, like the three-dot diff GitHub filters on. Null when it cannot be determined.
   */
  public @Nullable List<String> changedFilesOfPullRequest(
      final String ownerUsername,
      final String repoName,
      final String targetBranch,
      final String headSha) {

    final var repoPath = Path.of(this.repoRoot, ownerUsername, repoName + ".git");
    try (Repository gitRepo = new FileRepositoryBuilder().setGitDir(repoPath.toFile()).build();
        RevWalk revWalk = new RevWalk(gitRepo)) {
      final var headId = gitRepo.resolve(headSha + "^{commit}");
      final var targetId = gitRepo.resolve("refs/heads/" + targetBranch + "^{commit}");
      if (headId == null || targetId == null) {
        return null;
      }
      final var head = revWalk.parseCommit(headId);
      final var target = revWalk.parseCommit(targetId);
      revWalk.setRevFilter(RevFilter.MERGE_BASE);
      revWalk.markStart(head);
      revWalk.markStart(target);
      final var mergeBase = revWalk.next();
      return diffPaths(gitRepo, mergeBase, head);
    } catch (final IOException | RuntimeException e) {
      log.warn(
          "Cannot list PR changed files in {}/{}: {}", ownerUsername, repoName, e.getMessage());
      return null;
    }
  }

  private static boolean isUnknownSha(final @Nullable String sha) {
    return sha == null || sha.isBlank() || sha.chars().allMatch(c -> c == '0');
  }

  private static List<String> diffPaths(
      final Repository gitRepo, final @Nullable RevCommit base, final RevCommit head)
      throws IOException {

    final var paths = new java.util.LinkedHashSet<String>();
    try (DiffFormatter diff = new DiffFormatter(NullOutputStream.INSTANCE)) {
      diff.setRepository(gitRepo);
      for (final DiffEntry entry :
          diff.scan(base == null ? null : base.getTree(), head.getTree())) {
        if (entry.getChangeType() != DiffEntry.ChangeType.ADD) {
          paths.add(entry.getOldPath());
        }
        if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
          paths.add(entry.getNewPath());
        }
      }
    }
    return List.copyOf(paths);
  }

  private WorkflowModel readModel(final String content) throws IOException {
    return YAML_MAPPER.readValue(content, WorkflowModel.class);
  }

  private static String firstLine(final @Nullable String message) {
    return message == null ? "invalid YAML" : message.lines().findFirst().orElse("invalid YAML");
  }

  private Optional<WorkflowModel> parseYaml(final String path, final String content) {

    try {
      return java.util.Optional.of(this.readModel(content));
    } catch (final IOException e) {
      log.warn("Failed to parse workflow YAML at {}: {}", path, e.getMessage());
      return java.util.Optional.empty();
    }
  }

  private @Nullable String readBlob(final Repository repo, final ObjectId objectId)
      throws IOException {

    final var loader = repo.open(objectId);

    return loader == null ? null : new String(loader.getBytes(), StandardCharsets.UTF_8);
  }

  public @Nullable WorkflowModel parseFromContent(final String yamlContent) {

    return this.parseYaml("<stored>", yamlContent).orElse(null);
  }

  /** A workflow file that could not be parsed. */
  public record ParseFailure(String filePath, String reason) {}

  public record ParsedWorkflow(String filePath, String rawContent, WorkflowModel model) {}
}
