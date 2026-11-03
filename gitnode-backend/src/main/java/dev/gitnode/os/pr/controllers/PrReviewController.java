package dev.gitnode.os.pr.controllers;

import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.pr.services.PrReviewService;
import dev.gitnode.os.repo.api.RepoAccessPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls/{number}/reviews")
@RequiredArgsConstructor
public class PrReviewController {
  private final PrReviewService reviews;
  private final JwtUtils jwt;
  private final RepoAccessPort repos;

  public record ReviewForm(
      @NotBlank @Pattern(regexp = "APPROVED|CHANGES_REQUESTED|COMMENTED") String state,
      @Size(max = 65535) String body,
      @NotBlank @Pattern(regexp = "[0-9a-f]{40}") String expectedSha,
      @NotBlank @Pattern(regexp = "[0-9a-f]{40}") String expectedTargetSha) {}

  @GetMapping
  public PrReviewService.ReviewsInfo list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String auth)
      throws IOException {
    this.repos.assertUserCanAccessRepo(
        auth == null ? null : this.jwt.extractUserId(auth), owner, repo);
    return this.reviews.list(owner, repo, number);
  }

  @PostMapping
  public ResponseEntity<PrReviewService.ReviewInfo> submit(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final ReviewForm form)
      throws IOException {
    final var user = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(user, owner, repo);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            this.reviews.submit(
                owner,
                repo,
                number,
                user,
                form.state(),
                form.body(),
                form.expectedSha(),
                form.expectedTargetSha()));
  }
}
