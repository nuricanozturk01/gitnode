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
package com.nuricanozturk.originhub.actions.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nuricanozturk.originhub.actions.model.WorkflowModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
public class WorkflowParserService {

  static final String WORKFLOW_DIR = ".originhub/workflows";
  static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  /**
   * Reads and parses all workflow YAML files from the given branch of a bare git repository.
   * Returns only successfully parsed workflows; skips files with syntax errors.
   */
  public List<ParsedWorkflow> parseWorkflows(
      final String ownerUsername, final String repoName, final String branchRef) {

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
            final var path = treeWalk.getPathString();

            if (!path.endsWith(".yml") && !path.endsWith(".yaml")) {
              continue;
            }

            final var objectId = treeWalk.getObjectId(0);
            final var content = this.readBlob(gitRepo, objectId);

            if (content == null) {
              continue;
            }

            this.parseYaml(path, content)
                .ifPresent(model -> result.add(new ParsedWorkflow(path, content, model)));
          }
        }
      }
    } catch (final IOException e) {
      log.warn("Failed to read workflows from {}/{}: {}", ownerUsername, repoName, e.getMessage());
    }

    return List.copyOf(result);
  }

  private java.util.Optional<WorkflowModel> parseYaml(final String path, final String content) {

    try {
      return java.util.Optional.of(YAML_MAPPER.readValue(content, WorkflowModel.class));
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

  public record ParsedWorkflow(String filePath, String rawContent, WorkflowModel model) {}
}
