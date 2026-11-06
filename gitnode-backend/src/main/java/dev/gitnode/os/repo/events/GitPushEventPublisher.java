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
package dev.gitnode.os.repo.events;

import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.repo.cache.RepoCacheInvalidator;
import dev.gitnode.os.repo.repositories.RepoRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
public class GitPushEventPublisher implements PostReceiveHook {

  private final ApplicationEventPublisher eventPublisher;
  private final RepoRepository repoRepository;
  private final RepoCacheInvalidator cacheInvalidator;
  private static final int FOUR = 4;

  @Value("${gitnode.git.repo-root}")
  private String repoRoot;

  @Override
  @Transactional
  public void onPostReceive(final ReceivePack rp, final Collection<ReceiveCommand> commands) {
    this.publish(rp.getRepository().getDirectory(), commands, null);
  }

  @Transactional
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
                  .forEach(
                      command -> {
                        final var branch =
                            command.getRefName().substring(Constants.R_HEADS.length());
                        log.debug(
                            "Publishing push event: {}/{} branch={}", owner, repoName, branch);
                        this.cacheInvalidator.evictBranchScoped(owner, repoName, branch);
                        this.cacheInvalidator.evictBranches(owner, repoName);
                        final boolean deleted = isZero(command.getNewId());
                        this.eventPublisher.publishEvent(
                            new RepoPushedEvent(
                                repo.getId(),
                                branch,
                                pusherName,
                                deleted ? null : command.getNewId().name(),
                                command.getOldId().name()));
                        this.publishRefLifecycle(
                            repo.getId(), "branch", branch, command, pusherName);
                      });

              final var tagCommands =
                  okCommands.stream()
                      .filter(cmd -> cmd.getRefName().startsWith(Constants.R_TAGS))
                      .toList();
              if (!tagCommands.isEmpty()) {
                log.debug("Tag push detected: {}/{} — evicting TAGS cache", owner, repoName);
                this.cacheInvalidator.evictTags(owner, repoName);
                this.publishTagEvents(repo.getId(), tagCommands, pusherName);
              }
            });
  }

  private static boolean isZero(final org.eclipse.jgit.lib.ObjectId id) {
    return id.equals(org.eclipse.jgit.lib.ObjectId.zeroId());
  }

  /** A pushed ref that did not exist before is "created"; one that is gone now is "deleted". */
  private void publishRefLifecycle(
      final java.util.UUID repoId,
      final String refType,
      final String refName,
      final ReceiveCommand command,
      final String pusherName) {

    if (isZero(command.getOldId()) && !isZero(command.getNewId())) {
      this.eventPublisher.publishEvent(
          new dev.gitnode.os.events.repo.RepoRefCreatedEvent(
              repoId, refType, refName, command.getNewId().name(), pusherName));
    } else if (isZero(command.getNewId()) && !isZero(command.getOldId())) {
      this.eventPublisher.publishEvent(
          new dev.gitnode.os.events.repo.RepoRefDeletedEvent(repoId, refType, refName, pusherName));
    }
  }

  /** Created or moved tags can trigger push workflows; created and deleted ones also lifecycle. */
  private void publishTagEvents(
      final java.util.UUID repoId,
      final java.util.List<ReceiveCommand> tagCommands,
      final String pusherName) {

    for (final var command : tagCommands) {
      final var tagName = command.getRefName().substring(Constants.R_TAGS.length());
      this.publishRefLifecycle(repoId, "tag", tagName, command, pusherName);
      if (isZero(command.getNewId())) {
        continue;
      }
      this.eventPublisher.publishEvent(
          new dev.gitnode.os.events.repo.RepoTagPushedEvent(
              repoId, tagName, pusherName, command.getNewId().name(), command.getOldId().name()));
    }
  }

  @SuppressWarnings("checkstyle:IllegalCatch") // hook boundary: must never fail the push
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
