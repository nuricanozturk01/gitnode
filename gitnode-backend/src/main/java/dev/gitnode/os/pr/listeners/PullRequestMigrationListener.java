package dev.gitnode.os.pr.listeners;

import dev.gitnode.os.events.pr.PullRequestMigrationRequestedEvent;
import dev.gitnode.os.pr.dtos.PrForm;
import dev.gitnode.os.pr.services.PullRequestService;
import dev.gitnode.os.shared.branch.dtos.BranchForm;
import dev.gitnode.os.shared.branch.services.BranchProtocolService;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemAlreadyExistsException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
@NullMarked
public class PullRequestMigrationListener {

  private final PullRequestService prService;
  private final RestClient restClient;
  private final BranchProtocolService branchProtocolService;

  @EventListener
  public void onMigratePrRequested(final PullRequestMigrationRequestedEvent event) {

    final var owner = event.getRemoteRepoOwner();
    final var repoName = event.getRemoteRepoName();

    final var prs =
        this.restClient
            .get()
            .uri(
                "https://api.github.com/repos/{owner}/{repo}/pulls?state=all&per_page=100",
                owner,
                repoName)
            .header("Authorization", "Bearer " + event.getAccessToken())
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    if (prs == null || prs.isEmpty()) {
      return;
    }

    prs.forEach(pr -> this.createPr(pr, event));
  }

  private void createPr(
      final Map<String, Object> pr, final PullRequestMigrationRequestedEvent event) {

    final var repoName = event.getRemoteRepoName();
    final var tenantUsername = event.getTenantUsername();
    try {
      final var head = (Map<String, Object>) pr.get("head");
      final var base = (Map<String, Object>) pr.get("base");
      final var sourceBranch = (String) head.get("ref");
      final var targetBranch = (String) base.get("ref");
      final var isClosed = "closed".equals(pr.get("state"));

      this.ensureBranchExists(tenantUsername, repoName, sourceBranch, targetBranch);

      final var prForm = this.buildPrForm(pr);
      final var prDetail =
          this.prService.create(tenantUsername, repoName, event.getTenantId(), prForm);

      if (isClosed) {
        this.closePrAndCleanup(tenantUsername, repoName, prDetail.number(), sourceBranch);
      }
    } catch (final Exception e) {
      log.debug("PR migrate edilemedi: {} - {}", pr.get("number"), e.getMessage());
    }
  }

  private void ensureBranchExists(
      final String tenantUsername,
      final String repoName,
      final String sourceBranch,
      final String targetBranch) {

    try {
      final var branchForm = new BranchForm(sourceBranch, targetBranch);
      this.branchProtocolService.create(tenantUsername, repoName, branchForm);
    } catch (final ItemAlreadyExistsException _) {
      log.debug("Branch zaten mevcut, atlanıyor: {}", sourceBranch);
    } catch (final ItemNotFoundException _) {
      log.debug("Kaynak branch bulunamadı ({}), default branch'ten oluşturuluyor", targetBranch);
      try {
        final var fallbackForm = new BranchForm(sourceBranch, "main");
        this.branchProtocolService.create(tenantUsername, repoName, fallbackForm);
      } catch (final ItemAlreadyExistsException _) {
        log.debug("Branch zaten mevcut (fallback), atlanıyor: {}", sourceBranch);
      } catch (final Exception fallbackErr) {
        log.debug(
            "Fallback branch oluşturulamadı: {} - {}", sourceBranch, fallbackErr.getMessage());
      }
    } catch (final Exception e) {
      log.debug("Branch oluşturulamadı: {} - {}", sourceBranch, e.getMessage());
    }
  }

  private void closePrAndCleanup(
      final String tenantUsername,
      final String repoName,
      final int prNumber,
      final String sourceBranch) {

    try {
      this.prService.close(tenantUsername, repoName, prNumber);
    } catch (final Exception e) {
      log.debug("PR kapatılamadı: {} - {}", prNumber, e.getMessage());
    }

    try {
      this.branchProtocolService.delete(tenantUsername, repoName, sourceBranch);
    } catch (final Exception e) {
      log.debug("Branch silinemedi (PR kapatma): {} - {}", sourceBranch, e.getMessage());
    }
  }

  private PrForm buildPrForm(final Map<String, Object> pr) {
    final var head = (Map<String, Object>) pr.get("head");
    final var base = (Map<String, Object>) pr.get("base");

    final var form = new PrForm();
    form.setTitle((String) pr.get("title"));
    form.setDescription((String) pr.getOrDefault("body", ""));
    form.setSourceBranch((String) head.get("ref"));
    form.setTargetBranch((String) base.get("ref"));
    form.setIsDraft((Boolean) pr.getOrDefault("draft", false));
    return form;
  }
}
