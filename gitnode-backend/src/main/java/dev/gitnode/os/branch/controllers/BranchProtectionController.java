package dev.gitnode.os.branch.controllers;

import dev.gitnode.os.branch.entities.BranchProtectionRule;
import dev.gitnode.os.branch.services.BranchProtectionService;
import dev.gitnode.os.identity.services.JwtUtils;
import dev.gitnode.os.repo.api.CollaboratorPermission;
import dev.gitnode.os.repo.api.RepoAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos/{owner}/{repo}/branch-protections")
@RequiredArgsConstructor
public class BranchProtectionController {
  private final JwtUtils jwt;
  private final RepoAccessPort access;
  private final RepoLookupPort repos;
  private final BranchProtectionService protection;
  private final GitProvider git;

  public record ProtectionForm(
      @NotBlank @Size(max = 255) String branchName,
      @NotNull @Min(0) @Max(20) Integer requiredApprovals,
      @Size(max = 20)
          List<
                  @NotBlank @Size(max = 256)
                  @jakarta.validation.constraints.Pattern(regexp = "[^,]+") String>
              requiredChecks,
      Boolean requireConversationResolution) {}

  public record ProtectionInfo(
      UUID id,
      String branchName,
      int requiredApprovals,
      List<String> requiredChecks,
      boolean requireConversationResolution) {
    static ProtectionInfo from(final BranchProtectionRule rule) {
      return new ProtectionInfo(
          rule.getId(),
          rule.getBranchName(),
          rule.getRequiredApprovals(),
          rule.requiredCheckNames(),
          rule.isRequireConversationResolution());
    }
  }

  @GetMapping
  public List<ProtectionInfo> list(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    final var repoId = this.authorize(owner, repo, auth);
    return this.protection.list(repoId).stream().map(ProtectionInfo::from).toList();
  }

  @PutMapping
  public ProtectionInfo save(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth,
      @Valid @RequestBody final ProtectionForm form)
      throws IOException {
    final var repoId = this.authorize(owner, repo, auth);
    if (!Repository.isValidRefName(Constants.R_HEADS + form.branchName())) {
      throw new BadRequestException("Invalid branch name");
    }
    try (final var gitRepo = this.git.open(owner, repo)) {
      if (gitRepo.exactRef(Constants.R_HEADS + form.branchName()) == null) {
        throw new ItemNotFoundException("Branch not found");
      }
    }
    return ProtectionInfo.from(
        this.protection.save(
            repoId,
            form.branchName(),
            form.requiredApprovals(),
            form.requiredChecks() == null
                ? List.of()
                : form.requiredChecks().stream().map(String::strip).distinct().toList(),
            Boolean.TRUE.equals(form.requireConversationResolution())));
  }

  @DeleteMapping("/{ruleId}")
  public ResponseEntity<Void> delete(
      @PathVariable final String owner,
      @PathVariable final String repo,
      @PathVariable final UUID ruleId,
      @RequestHeader(HttpHeaders.AUTHORIZATION) final String auth) {
    this.protection.delete(this.authorize(owner, repo, auth), ruleId);
    return ResponseEntity.noContent().build();
  }

  private UUID authorize(final String owner, final String repo, final String auth) {
    final var requester = this.jwt.extractUserId(auth);
    this.access.assertUserHasPermission(
        requester, owner, repo, CollaboratorPermission.SETTINGS_WRITE);
    return this.repos
        .find(owner, repo)
        .orElseThrow(() -> new ItemNotFoundException("Repository not found"))
        .id();
  }
}
