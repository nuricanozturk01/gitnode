package dev.gitnode.os.ssh.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.gitnode.os.identity.api.TenantQueryPort.TenantRef;
import dev.gitnode.os.repo.api.GitAccessPort;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitNodeSshServer.assertAccess (RAPOR2 bug #1)")
class GitNodeSshServerAccessTest {

  @Mock private SshKeyService sshKeyService;
  @Mock private RepoLookupPort repoRepository;
  @Mock private GitAccessPort gitAccess;

  @InjectMocks private GitNodeSshServer sshServer;

  private Method assertAccessMethod;
  private Method parseRepoArgMethod;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    assertAccessMethod =
        GitNodeSshServer.class.getDeclaredMethod(
            "assertAccess", TenantRef.class, String.class, String.class, boolean.class);
    assertAccessMethod.setAccessible(true);
    parseRepoArgMethod = GitNodeSshServer.class.getDeclaredMethod("parseRepoArg", String[].class);
    parseRepoArgMethod.setAccessible(true);
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

  private String[] callParseRepoArg(String arg) throws IOException {
    try {
      return (String[])
          parseRepoArgMethod.invoke(sshServer, (Object) new String[] {"git-upload-pack", arg});
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof IOException ioe) throw ioe;
      throw new RuntimeException(e.getCause());
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static TenantRef tenant(String username) {
    return new TenantRef(UUID.randomUUID(), username, username + "@example.org", null, null, true);
  }

  @Test
  @DisplayName("write — denied without push access")
  void write_denied_withoutPushAccess() {
    TenantRef stranger = tenant("stranger");
    when(gitAccess.canWrite(stranger.id(), "alice", "myrepo")).thenReturn(false);

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", true))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Write access denied");
  }

  @Test
  @DisplayName("write — allowed for owner, collaborator or team member with PUSH")
  void write_allowed_withPushAccess() {
    TenantRef developer = tenant("dev");
    when(gitAccess.canWrite(developer.id(), "alice", "myrepo")).thenReturn(true);

    assertThatNoException().isThrownBy(() -> callAssertAccess(developer, "alice", "myrepo", true));
  }

  @Test
  @DisplayName("read — denied when the port refuses read")
  void read_denied_privateRepo_forStranger() {
    TenantRef stranger = tenant("stranger");
    when(gitAccess.canRead(stranger.id(), "alice", "myrepo")).thenReturn(false);

    assertThatThrownBy(() -> callAssertAccess(stranger, "alice", "myrepo", false))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Read access denied");
  }

  @Test
  @DisplayName("read — allowed when the port grants read")
  void read_allowed_whenPortGrants() {
    TenantRef viewer = tenant("viewer");
    when(gitAccess.canRead(viewer.id(), "alice", "myrepo")).thenReturn(true);

    assertThatNoException().isThrownBy(() -> callAssertAccess(viewer, "alice", "myrepo", false));
  }

  @Test
  @DisplayName("repo path — owner/repo with optional .git suffix, quotes and leading slash")
  void parseRepoArg_acceptsCanonicalForms() throws IOException {
    assertThat(callParseRepoArg("'/alice/my.github.io.git'"))
        .containsExactly("alice", "my.github.io");
    assertThat(callParseRepoArg("alice/repo")).containsExactly("alice", "repo");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "alice/public.git/../../bob/secret.git",
        "alice/../bob/secret.git",
        "alice/repo.git/extra",
        "../alice/repo.git",
        "alice/.git",
        "alice"
      })
  @DisplayName("repo path — traversal and extra segments are rejected")
  void parseRepoArg_rejectsTraversal(String arg) {
    assertThatThrownBy(() -> callParseRepoArg(arg))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Invalid repository path");
  }
}
