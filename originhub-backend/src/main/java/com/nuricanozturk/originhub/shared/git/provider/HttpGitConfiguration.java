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
package com.nuricanozturk.originhub.shared.git.provider;

import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.repositories.TenantRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "originhub.http.enabled", havingValue = "true")
public class HttpGitConfiguration {

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  @Bean
  public ServletRegistrationBean<GitServlet> gitServletRegistration(
      final RepoRepository repoRepository) {

    final var gitServlet = new GitServlet();
    gitServlet.setRepositoryResolver(
        (_, name) -> {
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
            return new org.eclipse.jgit.storage.file.FileRepositoryBuilder()
                .setGitDir(gitDir.toFile())
                .build();
          } catch (final IOException ex) {
            log.error("Failed to open repository: {}/{}", owner, repoName, ex);
            throw new org.eclipse.jgit.errors.RepositoryNotFoundException(name, ex);
          }
        });

    final var registration = new ServletRegistrationBean<>(gitServlet, "/git/*");
    registration.setName("GitServlet");
    registration.setLoadOnStartup(1);

    return registration;
  }

  @Bean
  public FilterRegistrationBean<HttpGitAuthenticationFilter> httpGitAuthenticationFilter(
      final TenantRepository tenantRepository) {

    final var filter = new HttpGitAuthenticationFilter(tenantRepository);
    final var registration = new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns("/git/*");
    registration.setOrder(1);

    return registration;
  }

  @Slf4j
  @RequiredArgsConstructor
  public static class HttpGitAuthenticationFilter implements Filter {

    private final TenantRepository tenantRepository;

    @Override
    public void doFilter(
        final ServletRequest request, final ServletResponse response, final FilterChain chain)
        throws IOException, ServletException {

      final var httpRequest = (HttpServletRequest) request;
      final var httpResponse = (HttpServletResponse) response;

      try {
        final var auth = httpRequest.getHeader("Authorization");

        if (auth != null && auth.startsWith("Basic ")) {
          this.authenticate(auth);
          chain.doFilter(request, response);
        } else {
          httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"OriginHub\"");
          httpResponse.getWriter().write("Unauthorized");
          log.debug("HTTP Git request without auth");
        }
      } catch (final IllegalArgumentException ex) {
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"OriginHub\"");
        httpResponse.getWriter().write("Unauthorized: " + ex.getMessage());
        log.warn("HTTP Git auth failed: {}", ex.getMessage());
      }
    }

    private void authenticate(final String authHeader) {
      try {
        final var base64 = authHeader.substring("Basic ".length());
        final var decoded = new String(Base64.getDecoder().decode(base64));
        final var parts = decoded.split(":", 2);

        if (parts.length != 2) {
          throw new IllegalArgumentException("Invalid Basic auth format");
        }

        final var username = parts[0].toLowerCase(Locale.getDefault());
        final var password = parts[1];

        final var tenant =
            this.tenantRepository
                .findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (tenant.getSalt() == null || tenant.getHash() == null) {
          throw new IllegalArgumentException("User password not configured");
        }

        final var hash = DigestUtils.sha256Hex(password + tenant.getSalt());

        if (!hash.equals(tenant.getHash())) {
          log.warn("HTTP Git password mismatch for user: {}", username);
          throw new IllegalArgumentException("Invalid password");
        }

        log.info("HTTP Git auth success: {}", username);
      } catch (final IllegalArgumentException ex) {
        throw ex;
      } catch (final Exception ex) {
        throw new IllegalArgumentException("Auth failed: " + ex.getMessage(), ex);
      }
    }
  }
}
