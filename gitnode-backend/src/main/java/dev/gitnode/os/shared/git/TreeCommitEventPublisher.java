package dev.gitnode.os.shared.git;

import dev.gitnode.os.events.repo.RepoPushedEvent;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TreeCommitEventPublisher {
  private final RepoRepository repos;
  private final ApplicationEventPublisher events;

  @Transactional
  public void publish(
      final String owner,
      final String repoName,
      final String branch,
      final String actor,
      final String commitSha) {
    final var repo =
        this.repos
            .findByOwnerUsernameAndName(owner, repoName)
            .orElseThrow(() -> new ItemNotFoundException("Repository not found"));
    this.events.publishEvent(new RepoPushedEvent(repo.getId(), branch, actor, commitSha));
  }
}
