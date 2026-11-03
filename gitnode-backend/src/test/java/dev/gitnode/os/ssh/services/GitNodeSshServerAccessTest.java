package dev.gitnode.os.ssh.services;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
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
@DisplayName("GitNodeSshServer.assertAccess (RAPOR2 bug #1)")
class GitNodeSshServerAccessTest {

  @Mock private SshKeyService sshKeyService;
  @Mock private RepoLookupPort repoRepository;

  @InjectMocks private GitNodeSshServer sshServer;

  private Method assertAccessMethod;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    assertAccessMethod =
        GitNodeSshServer.class.getDeclaredMethod(
            "assertAccess", TenantRef.class, String.class, String.class, boolean.class);
    assertAccessMethod.setAccessible(true);
  }

  private void callAssertAccess(TenantRef tenant, String owner, String repo, boolean isWrite)
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

  private static TenantRef tenant(String username) {
    return new TenantRef(UUID.randomUUID(), username, username + "@example.org", null, null, true);
  }

  private static RepoRef repo(boolean isPrivate) {
    return new RepoRef(UUID.randomUUID(), UUID.randomUUID(), "owner", "repo", isPrivate);
  }

  // -----------------------------------------------------------------------
  // write access
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("write — denied when requester is not repo owner")
  void write_denied_forNonOwner() {
    TenantRef stranger = tenant("stranger");

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", true))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Write access denied");
  }

  @Test
  @DisplayName("write — allowed when tenant username matches repo owner")
  void write_allowed_forOwner() {
    TenantRef owner = tenant("alice");
    RepoRef r = repo(false);
    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(owner, "alice", "myrepo", true));
  }

  // -----------------------------------------------------------------------
  // read access — private repo
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("read — denied on private repo for non-owner")
  void read_denied_privateRepo_forNonOwner() {
    TenantRef stranger = tenant("stranger");
    RepoRef r = repo(true);
    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", false))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Read access denied");
  }

  @Test
  @DisplayName("read — allowed on private repo for owner")
  void read_allowed_privateRepo_forOwner() {
    TenantRef owner = tenant("alice");
    RepoRef r = repo(true);
    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(owner, "alice", "myrepo", false));
  }

  // -----------------------------------------------------------------------
  // read access — public repo
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("read — allowed on public repo for anonymous / any user")
  void read_allowed_publicRepo_forStranger() {
    TenantRef stranger = tenant("stranger");
    RepoRef r = repo(false);
    when(repoRepository.find("alice", "myrepo")).thenReturn(Optional.of(r));

    assertThatNoException().isThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", false));
  }

  @Test
  @DisplayName("read — allowed on public repo when repo not found (no privacy check)")
  void read_allowed_whenRepoNotFound() {
    TenantRef stranger = tenant("stranger");
    when(repoRepository.find("alice", "ghost")).thenReturn(Optional.empty());

    assertThatNoException().isThrownBy(() -> callAssertAccess(stranger, "alice", "ghost", false));
  }
}
