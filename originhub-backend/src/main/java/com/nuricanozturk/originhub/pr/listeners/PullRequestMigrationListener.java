package com.nuricanozturk.originhub.pr.listeners;

import com.nuricanozturk.originhub.pr.dtos.PrForm;
import com.nuricanozturk.originhub.pr.services.PullRequestService;
import com.nuricanozturk.originhub.shared.branch.dtos.BranchForm;
import com.nuricanozturk.originhub.shared.branch.services.BranchProtocolService;
import com.nuricanozturk.originhub.shared.pr.events.PullRequestMigrationRequestedEvent;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class PullRequestMigrationListener {

  private final PullRequestService prService;
  private final RestClient restClient;
  private final BranchProtocolService branchProtocolService;

  @Async
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

      final var branchForm = new BranchForm(sourceBranch, targetBranch);
      this.branchProtocolService.create(tenantUsername, repoName, branchForm);

      final var prForm = this.buildPrForm(pr);
      final var prDetail =
          this.prService.create(tenantUsername, repoName, event.getTenantId(), prForm);

      if (isClosed) {
        this.prService.close(tenantUsername, repoName, prDetail.number());
        this.branchProtocolService.delete(tenantUsername, repoName, sourceBranch);
      }
    } catch (final Exception e) {
      log.debug("PR migrate edilemedi: {} - {}", pr.get("number"), e.getMessage());
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
