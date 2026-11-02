package dev.gitnode.os.issue.controllers;

import dev.gitnode.os.issue.dtos.IssueLabelForm;
import dev.gitnode.os.issue.dtos.IssueLabelInfo;
import dev.gitnode.os.issue.services.IssueLabelService;
import dev.gitnode.os.shared.auth.services.JwtUtils;
import dev.gitnode.os.shared.repo.services.RepoService;
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
@RequestMapping("/api/repos/{owner}/{repo}/labels")
@RequiredArgsConstructor
public class IssueLabelController {
  private final IssueLabelService labels;
  private final JwtUtils jwt;
  private final RepoService repos;

  @GetMapping
  public List<IssueLabelInfo> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String auth) {
    this.repos.assertUserCanAccessRepo(
        auth == null ? null : this.jwt.extractUserId(auth), owner, repo);
    return this.labels.list(owner, repo);
  }

  @PostMapping
  public ResponseEntity<IssueLabelInfo> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final IssueLabelForm form) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.labels.save(owner, repo, requester, null, form));
  }

  @PutMapping("/{labelId}")
  public IssueLabelInfo update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID labelId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final IssueLabelForm form) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    return this.labels.save(owner, repo, requester, labelId, form);
  }

  @DeleteMapping("/{labelId}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID labelId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    this.labels.delete(owner, repo, requester, labelId);
    return ResponseEntity.noContent().build();
  }
}
