package com.nuricanozturk.originhub.issue.controllers;

import com.nuricanozturk.originhub.issue.dtos.IssueCommentForm;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueCommentUpdateForm;
import com.nuricanozturk.originhub.issue.dtos.IssueDetail;
import com.nuricanozturk.originhub.issue.dtos.IssueForm;
import com.nuricanozturk.originhub.issue.dtos.IssueInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueLinkedTaskInfo;
import com.nuricanozturk.originhub.issue.dtos.IssueUpdateForm;
import com.nuricanozturk.originhub.issue.services.IssueService;
import com.nuricanozturk.originhub.shared.auth.services.JwtUtils;
import com.nuricanozturk.originhub.shared.repo.dtos.PageResponse;
import com.nuricanozturk.originhub.shared.repo.services.RepoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/issues")
@RequiredArgsConstructor
@NullMarked
public class IssueController {

  private final IssueService issueService;
  private final JwtUtils jwtUtils;
  private final RepoService repoService;

  @PostMapping
  public ResponseEntity<IssueDetail> create(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final IssueForm form) {

    final var authorId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(authorId, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.issueService.create(owner, repo, authorId, form));
  }

  @GetMapping
  public ResponseEntity<PageResponse<IssueInfo>> getAll(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestParam(defaultValue = "OPEN") final String status,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.issueService.getAll(owner, repo, status, page));
  }

  @GetMapping("/{number}")
  public ResponseEntity<IssueDetail> get(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.issueService.get(owner, repo, number));
  }

  @PatchMapping("/{number}")
  public ResponseEntity<IssueDetail> update(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final IssueUpdateForm form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.issueService.update(owner, repo, number, form, requesterId));
  }

  @DeleteMapping("/{number}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.issueService.delete(owner, repo, number, requesterId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{number}/linked-tasks")
  public ResponseEntity<List<IssueLinkedTaskInfo>> getLinkedTasks(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.issueService.getLinkedTasks(owner, repo, number));
  }

  @GetMapping("/{number}/comments")
  public ResponseEntity<PageResponse<IssueCommentInfo>> getComments(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestParam(defaultValue = "0") final int page,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String authHeader) {

    final var requesterId = authHeader != null ? this.jwtUtils.extractUserId(authHeader) : null;
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(this.issueService.getComments(owner, repo, number, page));
  }

  @PostMapping("/{number}/comments")
  public ResponseEntity<IssueCommentInfo> addComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final IssueCommentForm form) {

    final var authorId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(authorId, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.issueService.addComment(owner, repo, number, authorId, form));
  }

  @PatchMapping("/{number}/comments/{commentId}")
  public ResponseEntity<IssueCommentInfo> updateComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader,
      @Valid @RequestBody final IssueCommentUpdateForm form) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    return ResponseEntity.ok(
        this.issueService.updateComment(owner, repo, number, commentId, form, requesterId));
  }

  @DeleteMapping("/{number}/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @PathVariable final UUID commentId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String authHeader) {

    final var requesterId = this.jwtUtils.extractUserId(authHeader);
    this.repoService.assertUserCanAccessRepo(requesterId, owner, repo);
    this.issueService.deleteComment(owner, repo, number, commentId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
