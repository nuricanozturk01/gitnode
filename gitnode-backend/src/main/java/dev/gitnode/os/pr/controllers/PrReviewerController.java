package dev.gitnode.os.pr.controllers;

import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.pr.services.PrReviewerService;
import dev.gitnode.os.pr.services.PrReviewerService.Reviewer;
import dev.gitnode.os.repo.api.RepoAccessPort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls/{number}/reviewers")
@RequiredArgsConstructor
public class PrReviewerController {
  private final PrReviewerService reviewers;
  private final JwtUtils jwt;
  private final RepoAccessPort repos;

  public record ReviewerForm(
      @NotNull @Size(min = 1, max = 50) List<@NotBlank @Size(max = 255) String> usernames) {}

  @GetMapping
  public List<Reviewer> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) final String auth) {
    this.repos.assertUserCanAccessRepo(
        auth == null ? null : this.jwt.extractUserId(auth), owner, repo);
    return this.reviewers.list(owner, repo, number);
  }

  @PutMapping
  public List<Reviewer> replace(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final int number,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final ReviewerForm form) {
    final var requester = this.jwt.extractUserId(auth);
    this.repos.assertUserCanAccessRepo(requester, owner, repo);
    return this.reviewers.replace(owner, repo, number, requester, form.usernames());
  }
}
