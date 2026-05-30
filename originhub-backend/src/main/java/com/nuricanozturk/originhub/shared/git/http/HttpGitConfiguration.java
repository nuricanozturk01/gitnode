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
package com.nuricanozturk.originhub.shared.git.http;

import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import java.io.IOException;
import java.nio.file.Path;
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
@ConditionalOnProperty(name = "originhub.http.enabled", havingValue = "true")
@NullMarked
public class HttpGitConfiguration {

  private static final int HTTP_GIT_FILTER_ORDER = -102;

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  @Bean
  public ServletRegistrationBean<GitServlet> gitServletRegistration(
      final RepoRepository repoRepository) {

    final var gitServlet = new GitServlet();
    gitServlet.setReceivePackFactory((_, repo) -> this.setReceivePackFactory(repo));
    gitServlet.setRepositoryResolver((_, name) -> this.setRepositoryResolver(repoRepository, name));

    final var registration = new ServletRegistrationBean<>(gitServlet, "/git/*");
    registration.setName("GitServlet");
    registration.setLoadOnStartup(1);

    return registration;
  }

  @Bean
  public FilterRegistrationBean<HttpGitAuthenticationFilter> httpGitAuthenticationFilter(
      final TenantRepository tenantRepository, final RepoRepository repoRepository) {

    final var filter = new HttpGitAuthenticationFilter(tenantRepository, repoRepository);
    final var registration = new FilterRegistrationBean<>(filter);

    registration.addUrlPatterns("/git/*");
    registration.setOrder(HTTP_GIT_FILTER_ORDER);

    return registration;
  }

  private Repository setRepositoryResolver(final RepoRepository repoRepository, final String name)
      throws RepositoryNotFoundException {

    final var cleanName = name.replace(".git", "");
    final var parts = cleanName.split("/");

    if (parts.length < 2) {
      throw new RepositoryNotFoundException(name);
    }

    final var owner = parts[0];
    final var repoName = parts[1];

    final var repoOpt = repoRepository.findByOwnerUsernameAndName(owner, repoName);

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

  private ReceivePack setReceivePackFactory(final Repository repository) {

    final var rp = new ReceivePack(repository);
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);

    return rp;
  }
}
