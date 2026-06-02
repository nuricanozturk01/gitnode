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
package com.nuricanozturk.originhub.shared.git;

import com.nuricanozturk.originhub.shared.cache.RepoCacheInvalidator;
import com.nuricanozturk.originhub.shared.repo.events.RepoPushedEvent;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import java.nio.file.Path;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class GitPushEventPublisher implements PostReceiveHook {

  private final ApplicationEventPublisher eventPublisher;
  private final RepoRepository repoRepository;
  private final RepoCacheInvalidator cacheInvalidator;
  private static final int FOUR = 4;

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  @Override
  public void onPostReceive(final ReceivePack rp, final Collection<ReceiveCommand> commands) {
    this.publish(rp.getRepository().getDirectory(), commands, null);
  }

  public void onPostReceive(
      final ReceivePack rp,
      final Collection<ReceiveCommand> commands,
      final @Nullable String pusher) {
    this.publish(rp.getRepository().getDirectory(), commands, pusher);
  }

  private void publish(
      final java.io.File gitDir,
      final Collection<ReceiveCommand> commands,
      final @Nullable String pusher) {

    final var parts = this.resolveOwnerAndRepo(gitDir);
    if (parts == null) {
      return;
    }

    final var owner = parts[0];
    final var repoName = parts[1];

    this.repoRepository
        .findByOwnerUsernameAndName(owner, repoName)
        .ifPresent(
            repo -> {
              final var pusherName = pusher != null ? pusher : "unknown";
              final var okCommands =
                  commands.stream()
                      .filter(cmd -> cmd.getResult() == ReceiveCommand.Result.OK)
                      .toList();

              okCommands.stream()
                  .filter(cmd -> cmd.getRefName().startsWith(Constants.R_HEADS))
                  .map(cmd -> cmd.getRefName().substring(Constants.R_HEADS.length()))
                  .distinct()
                  .forEach(
                      branch -> {
                        log.debug(
                            "Publishing push event: {}/{} branch={}", owner, repoName, branch);
                        this.cacheInvalidator.evictBranchScoped(owner, repoName, branch);
                        this.cacheInvalidator.evictBranches(owner, repoName);
                        this.eventPublisher.publishEvent(
                            new RepoPushedEvent(repo.getId(), branch, pusherName));
                      });

              final var hasTagPush =
                  okCommands.stream()
                      .anyMatch(cmd -> cmd.getRefName().startsWith(Constants.R_TAGS));
              if (hasTagPush) {
                log.debug("Tag push detected: {}/{} — evicting TAGS cache", owner, repoName);
                this.cacheInvalidator.evictTags(owner, repoName);
              }
            });
  }

  private String @Nullable [] resolveOwnerAndRepo(final java.io.File gitDir) {
    try {
      final var root = Path.of(this.repoRoot).toAbsolutePath().normalize();
      final var dir = gitDir.toPath().toAbsolutePath().normalize();
      final var relative = root.relativize(dir);

      if (relative.getNameCount() < 2) {
        return null;
      }

      final var owner = relative.getName(0).toString();
      var repoName = relative.getName(1).toString();
      if (repoName.endsWith(".git")) {
        repoName = repoName.substring(0, repoName.length() - FOUR);
      }

      return new String[] {owner, repoName};
    } catch (final Exception ex) {
      log.warn("Could not resolve owner/repo from git dir: {}", gitDir, ex);
      return null;
    }
  }
}
