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
package dev.gitnode.os.githttp;

import dev.gitnode.os.branch.api.ProtectedBranchReceiveHook;
import dev.gitnode.os.githttp.api.RunnerTokenPort;
import dev.gitnode.os.identity.api.GitCredentialPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.events.GitPushEventPublisher;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ReceivePack;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "gitnode.http.enabled", havingValue = "true")
@NullMarked
public class HttpGitConfiguration {

  private static final int HTTP_GIT_FILTER_ORDER = -102;

  @Value("${gitnode.git.repo-root}")
  private String repoRoot;

  @Bean
  public ServletRegistrationBean<GitServlet> gitServletRegistration(
      final RepoLookupPort repoLookup,
      final GitPushEventPublisher pushEventPublisher,
      final ProtectedBranchReceiveHook protectionHook) {

    final var gitServlet = new GitServlet();
    gitServlet.setReceivePackFactory(
        (_, repo) -> this.setReceivePackFactory(repo, pushEventPublisher, protectionHook));
    gitServlet.setRepositoryResolver((_, name) -> this.setRepositoryResolver(repoLookup, name));

    final var registration = new ServletRegistrationBean<>(gitServlet, "/git/*");
    registration.setName("GitServlet");
    registration.setLoadOnStartup(1);

    return registration;
  }

  @Bean
  public FilterRegistrationBean<HttpGitAuthenticationFilter> httpGitAuthenticationFilter(
      final GitCredentialPort credentials,
      final RepoLookupPort repoLookup,
      final Optional<RunnerTokenPort> runnerTokenPort) {

    final var filter =
        new HttpGitAuthenticationFilter(credentials, repoLookup, runnerTokenPort.orElse(null));
    final var registration = new FilterRegistrationBean<>(filter);

    registration.addUrlPatterns("/git/*");
    registration.setOrder(HTTP_GIT_FILTER_ORDER);

    return registration;
  }

  private Repository setRepositoryResolver(final RepoLookupPort repoLookup, final String name)
      throws RepositoryNotFoundException {

    final var cleanName = name.replace(".git", "");
    final var parts = cleanName.split("/");

    if (parts.length < 2) {
      throw new RepositoryNotFoundException(name);
    }

    final var owner = parts[0];
    final var repoName = parts[1];

    final var repoOpt = repoLookup.find(owner, repoName);

    if (repoOpt.isEmpty()) {
      throw new RepositoryNotFoundException(name);
    }

    try {
      final var gitDir = Path.of(this.repoRoot, owner, repoName + ".git");
      return new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
    } catch (final IOException ex) {
      log.debug("Failed to open repository: {}/{}", owner, repoName, ex);
      throw new RepositoryNotFoundException(name, ex);
    }
  }

  private ReceivePack setReceivePackFactory(
      final Repository repository,
      final GitPushEventPublisher pushEventPublisher,
      final ProtectedBranchReceiveHook protectionHook) {

    final var rp = new ReceivePack(repository);
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setPreReceiveHook(protectionHook);
    rp.setPostReceiveHook(pushEventPublisher);

    return rp;
  }
}
