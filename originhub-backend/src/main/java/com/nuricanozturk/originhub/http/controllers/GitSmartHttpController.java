/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nuricanozturk.originhub.http.controllers;

import com.nuricanozturk.originhub.http.services.HttpGitAccessValidator;
import com.nuricanozturk.originhub.http.services.HttpGitAuthenticator;
import com.nuricanozturk.originhub.shared.git.provider.GitProvider;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/git")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "originhub.http.enabled", havingValue = "true")
public class GitSmartHttpController {

  private static final Pattern REPO_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

  private final @NonNull HttpGitAuthenticator authenticator;
  private final @NonNull HttpGitAccessValidator accessValidator;
  private final @NonNull GitProvider gitProvider;
  private final @NonNull RepoRepository repoRepository;

  @GetMapping("/{owner}/{repo}/info/refs")
  public void getInfoRefs(
      @PathVariable @NonNull String owner,
      @PathVariable @NonNull String repo,
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response)
      throws IOException {

    this.validatePath(owner, repo);

    com.nuricanozturk.originhub.shared.tenant.entities.Tenant tenant;
    String service;
    boolean isWrite;

    try {
      tenant = this.authenticator.authenticate(request);
      service = request.getParameter("service");
      isWrite = "receive-pack".equals(service);
      this.accessValidator.assertAccess(tenant, owner, repo, isWrite);
    } catch (final IllegalArgumentException ex) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("text/plain");
      response.getWriter().write("Unauthorized: " + ex.getMessage());
      log.warn("HTTP Git auth failed: {}", ex.getMessage());
      return;
    }

    try (final var repository = this.gitProvider.open(owner, repo)) {
      response.setContentType("application/x-" + service + "-advertisement");
      response.addHeader("Cache-Control", "no-cache");

      final var output = response.getOutputStream();
      this.sendAdvertisement(output, service, repository);

      log.info(
          "Git HTTP refs request: user={}, repo={}/{}, service={}, write={}",
          tenant.getUsername(),
          owner,
          repo,
          service,
          isWrite);
    }
  }

  @PostMapping("/{owner}/{repo}/git-upload-pack")
  public void uploadPack(
      final @PathVariable String owner,
      final @PathVariable String repo,
      final HttpServletRequest request,
      final HttpServletResponse response)
      throws IOException {

    this.validatePath(owner, repo);

    com.nuricanozturk.originhub.shared.tenant.entities.Tenant tenant;
    try {
      tenant = this.authenticator.authenticate(request);
      this.accessValidator.assertAccess(tenant, owner, repo, false);
    } catch (final IllegalArgumentException ex) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("text/plain");
      response.getWriter().write("Unauthorized: " + ex.getMessage());
      log.warn("HTTP Git auth failed: {}", ex.getMessage());
      return;
    }

    log.info("Git HTTP upload-pack: user={}, repo={}/{}", tenant.getUsername(), owner, repo);

    try (final var repository = this.gitProvider.open(owner, repo)) {
      response.setContentType("application/x-upload-pack-result");
      final var input = request.getInputStream();
      final var output = response.getOutputStream();

      final var uploadPack = new org.eclipse.jgit.transport.UploadPack(repository);
      uploadPack.upload(input, output, null);
    }
  }

  @PostMapping("/{owner}/{repo}/git-receive-pack")
  public void receivePack(
      @PathVariable @NonNull String owner,
      @PathVariable @NonNull String repo,
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response)
      throws IOException {

    this.validatePath(owner, repo);

    com.nuricanozturk.originhub.shared.tenant.entities.Tenant tenant;
    try {
      tenant = this.authenticator.authenticate(request);
      this.accessValidator.assertAccess(tenant, owner, repo, true);
    } catch (final IllegalArgumentException ex) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("text/plain");
      response.getWriter().write("Unauthorized: " + ex.getMessage());
      log.warn("HTTP Git auth failed: {}", ex.getMessage());
      return;
    }

    log.info("Git HTTP receive-pack: user={}, repo={}/{}", tenant.getUsername(), owner, repo);

    try (final var repository = this.gitProvider.open(owner, repo)) {
      response.setContentType("application/x-receive-pack-result");
      final var input = request.getInputStream();
      final var output = response.getOutputStream();

      final var receivePack = new org.eclipse.jgit.transport.ReceivePack(repository);
      receivePack.receive(input, output, null);
    }
  }

  private void validatePath(final @NonNull String owner, final @NonNull String repo) {
    if (!REPO_PATH_PATTERN.matcher(owner).matches()
        || !REPO_PATH_PATTERN.matcher(repo).matches()) {
      throw new IllegalArgumentException("Invalid repository path");
    }
  }

  private void sendAdvertisement(
      final java.io.OutputStream output,
      final @NonNull String service,
      final @NonNull Repository repository)
      throws IOException {

    final var pckOut = new org.eclipse.jgit.transport.PacketLineOut(output);

    if ("git-upload-pack".equals(service)) {
      pckOut.writeString("# service=" + service + "\n");
      pckOut.end();
      final var uploadPack = new org.eclipse.jgit.transport.UploadPack(repository);
      uploadPack.sendAdvertisedRefs(new org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser(pckOut));
    } else if ("git-receive-pack".equals(service)) {
      pckOut.writeString("# service=" + service + "\n");
      pckOut.end();
      final var receivePack = new org.eclipse.jgit.transport.ReceivePack(repository);
      receivePack.sendAdvertisedRefs(new org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser(pckOut));
    }
  }
}
