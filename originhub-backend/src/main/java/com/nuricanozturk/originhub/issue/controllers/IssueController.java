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
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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
public class IssueController {

  private final @NonNull IssueService issueService;
  private final @NonNull JwtUtils jwtUtils;

  @PostMapping
  public @NonNull ResponseEntity<IssueDetail> create(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final @NonNull String authHeader,
      @Valid @RequestBody final @NonNull IssueForm form) {

    final var authorId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.issueService.create(owner, repo, authorId, form));
  }

  @GetMapping
  public @NonNull ResponseEntity<PageResponse<IssueInfo>> getAll(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @RequestParam(defaultValue = "OPEN") final @NonNull String status,
      @RequestParam(defaultValue = "0") final int page) {

    return ResponseEntity.ok(this.issueService.getAll(owner, repo, status, page));
  }

  @GetMapping("/{number}")
  public @NonNull ResponseEntity<IssueDetail> get(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number) {

    return ResponseEntity.ok(this.issueService.get(owner, repo, number));
  }

  @PatchMapping("/{number}")
  public @NonNull ResponseEntity<IssueDetail> update(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number,
      @Valid @RequestBody final @NonNull IssueUpdateForm form) {

    return ResponseEntity.ok(this.issueService.update(owner, repo, number, form));
  }

  @DeleteMapping("/{number}")
  public @NonNull ResponseEntity<Void> delete(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number) {

    this.issueService.delete(owner, repo, number);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{number}/linked-tasks")
  public @NonNull ResponseEntity<List<IssueLinkedTaskInfo>> getLinkedTasks(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number) {

    return ResponseEntity.ok(this.issueService.getLinkedTasks(owner, repo, number));
  }

  @GetMapping("/{number}/comments")
  public @NonNull ResponseEntity<PageResponse<IssueCommentInfo>> getComments(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number,
      @RequestParam(defaultValue = "0") final int page) {

    return ResponseEntity.ok(this.issueService.getComments(owner, repo, number, page));
  }

  @PostMapping("/{number}/comments")
  public @NonNull ResponseEntity<IssueCommentInfo> addComment(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final @NonNull String authHeader,
      @Valid @RequestBody final @NonNull IssueCommentForm form) {

    final var authorId = this.jwtUtils.extractUserId(authHeader);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.issueService.addComment(owner, repo, number, authorId, form));
  }

  @PatchMapping("/{number}/comments/{commentId}")
  public @NonNull ResponseEntity<IssueCommentInfo> updateComment(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number,
      @PathVariable final @NonNull UUID commentId,
      @Valid @RequestBody final @NonNull IssueCommentUpdateForm form) {

    return ResponseEntity.ok(this.issueService.updateComment(owner, repo, number, commentId, form));
  }

  @DeleteMapping("/{number}/comments/{commentId}")
  public @NonNull ResponseEntity<Void> deleteComment(
      @PathVariable final @NonNull String owner,
      @PathVariable final @NonNull String repo,
      @PathVariable final int number,
      @PathVariable final @NonNull UUID commentId) {

    this.issueService.deleteComment(owner, repo, number, commentId);
    return ResponseEntity.noContent().build();
  }
}
