package com.nuricanozturk.originhub.ssh.services;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.repo.entities.Repo;
import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OriginHubSshServer.assertAccess (RAPOR2 bug #1)")
class OriginHubSshServerAccessTest {

  @Mock private SshKeyService sshKeyService;
  @Mock private RepoRepository repoRepository;

  @InjectMocks private OriginHubSshServer sshServer;

  private Method assertAccessMethod;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    assertAccessMethod =
        OriginHubSshServer.class.getDeclaredMethod(
            "assertAccess", Tenant.class, String.class, String.class, boolean.class);
    assertAccessMethod.setAccessible(true);
  }

  private void callAssertAccess(Tenant tenant, String owner, String repo, boolean isWrite)
      throws IOException {
    try {
      assertAccessMethod.invoke(sshServer, tenant, owner, repo, isWrite);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof IOException ioe) throw ioe;
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static Tenant tenant(String username, boolean admin) {
    Tenant t = new Tenant();
    t.setId(UUID.randomUUID());
    t.setUsername(username);
    t.setAdmin(admin);
    return t;
  }

  private static Repo repo(boolean isPrivate) {
    Repo r = new Repo();
    r.setId(UUID.randomUUID());
    r.setPrivate(isPrivate);
    return r;
  }

  // -----------------------------------------------------------------------
  // write access
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("write — denied when requester is not owner and not admin")
  void write_denied_forNonOwner() {
    Tenant stranger = tenant("stranger", false);

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", true))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Write access denied");
  }

  @Test
  @DisplayName("write — allowed when tenant username matches repo owner")
  void write_allowed_forOwner() {
    Tenant owner = tenant("alice", false);
    Repo r = repo(false);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(owner, "alice", "myrepo", true));
  }

  @Test
  @DisplayName("write — allowed for admin even when username differs from repo owner")
  void write_allowed_forAdmin() {
    Tenant admin = tenant("adminuser", true);
    Repo r = repo(false);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(admin, "alice", "myrepo", true));
  }

  // -----------------------------------------------------------------------
  // read access — private repo
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("read — denied on private repo for non-owner")
  void read_denied_privateRepo_forNonOwner() {
    Tenant stranger = tenant("stranger", false);
    Repo r = repo(true);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", false))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Read access denied");
  }

  @Test
  @DisplayName("read — allowed on private repo for owner")
  void read_allowed_privateRepo_forOwner() {
    Tenant owner = tenant("alice", false);
    Repo r = repo(true);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(owner, "alice", "myrepo", false));
  }

  @Test
  @DisplayName("read — allowed on private repo for admin")
  void read_allowed_privateRepo_forAdmin() {
    Tenant admin = tenant("adminuser", true);
    Repo r = repo(true);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(admin, "alice", "myrepo", false));
  }

  // -----------------------------------------------------------------------
  // read access — public repo
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("read — allowed on public repo for anonymous / any user")
  void read_allowed_publicRepo_forStranger() {
    Tenant stranger = tenant("stranger", false);
    Repo r = repo(false);
    when(repoRepository.findByOwnerUsernameAndName("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", false));
  }

  @Test
  @DisplayName("read — allowed on public repo when repo not found (no privacy check)")
  void read_allowed_whenRepoNotFound() {
    Tenant stranger = tenant("stranger", false);
    when(repoRepository.findByOwnerUsernameAndName("alice", "ghost")).thenReturn(Optional.empty());

    assertThatNoException().isThrownBy(() -> callAssertAccess(stranger, "alice", "ghost", false));
  }
}
