package dev.gitnode.os.issue.controllers;

import dev.gitnode.os.identity.api.TokenUserResolver;
import dev.gitnode.os.issue.dtos.IssueMilestoneForm;
import dev.gitnode.os.issue.dtos.IssueMilestoneInfo;
import dev.gitnode.os.issue.services.IssueMilestoneService;
import dev.gitnode.os.repo.api.RepoAccessPort;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/milestones")
@RequiredArgsConstructor
public class IssueMilestoneController {
  private final IssueMilestoneService milestones;
  private final TokenUserResolver jwt;
  private final RepoAccessPort repos;

  @GetMapping
  public List<IssueMilestoneInfo> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String auth) {
    this.repos.assertUserCanAccessRepo(
        auth == null ? null : this.jwt.extractUserId(auth), owner, repo);
    return this.milestones.list(owner, repo);
  }

  @PostMapping
  public ResponseEntity<IssueMilestoneInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final IssueMilestoneForm form) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.milestones.save(owner, repo, requester, null, form));
  }

  @PutMapping("/{milestoneId}")
  public IssueMilestoneInfo update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID milestoneId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final IssueMilestoneForm form) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    return this.milestones.save(owner, repo, requester, milestoneId, form);
  }

  @DeleteMapping("/{milestoneId}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID milestoneId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    this.milestones.delete(owner, repo, requester, milestoneId);
    return ResponseEntity.noContent().build();
  }
}
