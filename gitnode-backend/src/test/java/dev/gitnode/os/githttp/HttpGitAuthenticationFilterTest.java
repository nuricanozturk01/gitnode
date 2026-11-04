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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.githttp.api.RunnerTokenPort;
import dev.gitnode.os.identity.api.GitCredentialPort;
import dev.gitnode.os.identity.api.TenantQueryPort;
import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.GitAccessPort;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("HttpGitAuthenticationFilter authorization")
class HttpGitAuthenticationFilterTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private GitCredentialPort credentials;
  @Mock private GitAccessPort gitAccess;
  @Mock private TenantQueryPort tenants;
  @Mock private RunnerTokenPort runnerTokenPort;
  @Mock private FilterChain chain;

  private HttpGitAuthenticationFilter filter;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    this.filter = new HttpGitAuthenticationFilter(credentials, gitAccess, tenants, runnerTokenPort);
    this.response = new MockHttpServletResponse();
  }

  private static MockHttpServletRequest uploadPack() {
    final var request = new MockHttpServletRequest("POST", "/git/alice/repo.git/git-upload-pack");
    request.setServletPath("/git");
    request.setPathInfo("/alice/repo.git/git-upload-pack");
    return request;
  }

  private static MockHttpServletRequest receivePack() {
    final var request = new MockHttpServletRequest("POST", "/git/alice/repo.git/git-receive-pack");
    request.setServletPath("/git");
    request.setPathInfo("/alice/repo.git/git-receive-pack");
    return request;
  }

  private static MockHttpServletRequest receivePackAdvertisement() {
    final var request = new MockHttpServletRequest("GET", "/git/alice/repo.git/info/refs");
    request.setServletPath("/git");
    request.setPathInfo("/alice/repo.git/info/refs");
    request.setQueryString("service=git-receive-pack");
    request.setParameter("service", "git-receive-pack");
    return request;
  }

  private static MockHttpServletRequest uploadPackAdvertisement() {
    final var request = new MockHttpServletRequest("GET", "/git/alice/repo.git/info/refs");
    request.setServletPath("/git");
    request.setPathInfo("/alice/repo.git/info/refs");
    request.setQueryString("service=git-upload-pack");
    request.setParameter("service", "git-upload-pack");
    return request;
  }

  private static MockHttpServletRequest withBasic(
      final MockHttpServletRequest request, final String username, final String password) {
    final var token =
        Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    request.addHeader("Authorization", "Basic " + token);
    return request;
  }

  private void stubUser(final String username) {
    when(tenants.findByUsername(username))
        .thenReturn(
            Optional.of(
                new TenantRef(USER_ID, username, username + "@example.org", null, null, true)));
  }

  @Test
  @DisplayName("authenticated stranger cloning a private repo gets 404")
  void strangerCloningPrivateRepo_404() throws Exception {
    stubUser("stranger");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(false);

    filter.doFilter(withBasic(uploadPackAdvertisement(), "stranger", "pw"), response, chain);

    assertThat(response.getStatus()).isEqualTo(404);
    verify(chain, never()).doFilter(any(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "x.github.io,x.github.io",
    "my.gitops,my.gitops",
    "plain.git,plain",
    "evil.git.git,evil.git",
    "x.github.io.git,x.github.io"
  })
  @DisplayName("only a trailing .git is stripped from the repository segment")
  void trailingGitOnlyStripped(final String segment, final String expectedRepo) throws Exception {
    stubUser("bob");
    when(gitAccess.canRead(USER_ID, "alice", expectedRepo)).thenReturn(true);
    final var raw =
        new MockHttpServletRequest("POST", "/git/alice/" + segment + "/git-upload-pack");
    raw.setServletPath("/git");
    raw.setPathInfo("/alice/" + segment + "/git-upload-pack");
    final var request = withBasic(raw, "bob", "pw");

    filter.doFilter(request, response, chain);

    verify(gitAccess).canRead(USER_ID, "alice", expectedRepo);
    verify(chain).doFilter(any(AuthenticatedRequest.class), any());
  }

  @Test
  @DisplayName("collaborator with PUSH can receive-pack")
  void collaboratorWithPush_canReceivePack() throws Exception {
    stubUser("bob");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(true);
    when(gitAccess.canWrite(USER_ID, "alice", "repo")).thenReturn(true);

    filter.doFilter(withBasic(receivePack(), "bob", "pw"), response, chain);

    verify(chain).doFilter(any(AuthenticatedRequest.class), any());
  }

  @Test
  @DisplayName("read-only collaborator gets 403 on receive-pack and its advertisement")
  void readOnlyCollaborator_receivePack_403() throws Exception {
    stubUser("bob");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(true);
    when(gitAccess.canWrite(USER_ID, "alice", "repo")).thenReturn(false);

    filter.doFilter(withBasic(receivePackAdvertisement(), "bob", "pw"), response, chain);
    assertThat(response.getStatus()).isEqualTo(403);

    final var second = new MockHttpServletResponse();
    filter.doFilter(withBasic(receivePack(), "bob", "pw"), second, chain);
    assertThat(second.getStatus()).isEqualTo(403);

    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  @DisplayName("team VIEWER can clone a private repo but cannot push")
  void teamViewer_canClonePrivate_cannotPush() throws Exception {
    stubUser("viewer");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(true);
    when(gitAccess.canWrite(USER_ID, "alice", "repo")).thenReturn(false);

    filter.doFilter(withBasic(uploadPack(), "viewer", "pw"), response, chain);
    verify(chain).doFilter(any(AuthenticatedRequest.class), any());

    final var pushResponse = new MockHttpServletResponse();
    filter.doFilter(withBasic(receivePack(), "viewer", "pw"), pushResponse, chain);
    assertThat(pushResponse.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("stranger pushing to a private repo gets 404, not 403")
  void strangerPushingPrivateRepo_404() throws Exception {
    stubUser("stranger");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(false);

    filter.doFilter(withBasic(receivePack(), "stranger", "pw"), response, chain);

    assertThat(response.getStatus()).isEqualTo(404);
    verify(gitAccess, never()).canWrite(any(), any(), any());
  }

  @Test
  @DisplayName("encoded receive-pack path is still treated as a write")
  void encodedReceivePackPath_treatedAsWrite() throws Exception {
    stubUser("viewer");
    when(gitAccess.canRead(USER_ID, "alice", "repo")).thenReturn(true);
    when(gitAccess.canWrite(USER_ID, "alice", "repo")).thenReturn(false);
    final var request =
        new MockHttpServletRequest("POST", "/git/alice/repo.git/git%2Dreceive-pack");
    request.setServletPath("/git");
    request.setPathInfo("/alice/repo.git/git-receive-pack");

    filter.doFilter(withBasic(request, "viewer", "pw"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("runner token can clone but cannot push")
  void runnerCanCloneButNotPush() throws Exception {
    when(runnerTokenPort.isValid("jwt")).thenReturn(true);

    filter.doFilter(withBasic(uploadPack(), "x-token", "jwt"), response, chain);
    verify(chain).doFilter(any(AuthenticatedRequest.class), any());

    final var pushResponse = new MockHttpServletResponse();
    filter.doFilter(withBasic(receivePack(), "x-token", "jwt"), pushResponse, chain);
    assertThat(pushResponse.getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("anonymous clone of a public repo is unchanged")
  void publicRepoAnonymousClone_unchanged() throws Exception {
    when(gitAccess.canRead(null, "alice", "repo")).thenReturn(true);
    final var request = uploadPackAdvertisement();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  @DisplayName("anonymous request for a private repo or a push is challenged with 401")
  void anonymousPrivateOrPush_401() throws Exception {
    when(gitAccess.canRead(null, "alice", "repo")).thenReturn(false);

    filter.doFilter(uploadPackAdvertisement(), response, chain);
    assertThat(response.getStatus()).isEqualTo(401);

    final var pushResponse = new MockHttpServletResponse();
    filter.doFilter(receivePack(), pushResponse, chain);
    assertThat(pushResponse.getStatus()).isEqualTo(401);

    verify(chain, never()).doFilter(any(), any());
  }
}
