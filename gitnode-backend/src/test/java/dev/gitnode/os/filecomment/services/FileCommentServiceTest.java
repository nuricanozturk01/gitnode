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

package dev.gitnode.os.filecomment.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.filecomment.dtos.FileCommentForm;
import dev.gitnode.os.filecomment.entities.FileComment;
import dev.gitnode.os.filecomment.repositories.FileCommentRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import dev.gitnode.os.shared.repo.entities.Repo;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("FileCommentService with a real Git repository")
class FileCommentServiceTest {

  @TempDir Path dir;

  private final List<FileComment> store = new ArrayList<>();
  private final FileCommentRepository comments = mock(FileCommentRepository.class);
  private final RepoRepository repos = mock(RepoRepository.class);
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final GitProvider provider = mock(GitProvider.class);
  private final org.springframework.context.ApplicationEventPublisher events =
      mock(org.springframework.context.ApplicationEventPublisher.class);
  private FileCommentService service;
  private Git git;
  private Tenant alice;
  private Tenant bob;
  private Tenant repoOwner;

  private Tenant user(final String name) {
    final var tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setUsername(name);
    tenant.setEmail(name + "@example.org");
    when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
    return tenant;
  }

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call();
    alice = user("alice");
    bob = user("bob");
    repoOwner = user("owner");
    final var repo = new Repo();
    repo.setId(UUID.randomUUID());
    repo.setOwner(repoOwner);
    when(repos.findByOwnerUsernameAndName("o", "r")).thenReturn(Optional.of(repo));
    when(provider.open("o", "r")).thenAnswer(i -> Git.open(dir.toFile()).getRepository());
    when(comments.save(any(FileComment.class)))
        .thenAnswer(
            invocation -> {
              final FileComment saved = invocation.getArgument(0);
              if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                store.add(saved);
              }
              return saved;
            });
    when(comments.findForPath(any(), any(), any())).thenAnswer(i -> List.copyOf(store));
    when(comments.findInRepo(any(), any()))
        .thenAnswer(
            i -> store.stream().filter(c -> c.getId().equals(i.getArgument(0))).findFirst());
    service = new FileCommentService(comments, repos, tenants, provider, events);
  }

  private void commit(final String file, final String content) throws Exception {
    Files.createDirectories(dir.resolve(file).getParent());
    Files.writeString(dir.resolve(file), content);
    git.add().addFilepattern(".").call();
    git.commit().setMessage("change " + file).setAuthor("a", "a@example.org").call();
  }

  private FileCommentForm form(final String path, final Integer line, final String body) {
    return new FileCommentForm("main", path, line, body, null);
  }

  @Test
  @DisplayName("comments are anchored to the head commit and listed as current")
  void createAndList() throws Exception {
    commit("src/a.txt", "one\ntwo\nthree\n");

    final var created =
        service.create("o", "r", alice.getId(), form("src/a.txt", 2, " Nice line "));
    final var page = service.list("o", "r", "main", "src/a.txt", alice.getId());

    assertThat(created.body()).isEqualTo("Nice line");
    assertThat(created.line()).isEqualTo(2);
    assertThat(created.outdated()).isFalse();
    assertThat(page.comments()).hasSize(1);
    assertThat(page.headSha()).isEqualTo(created.commitSha());
    assertThat(page.comments().get(0).canEdit()).isTrue();
    assertThat(service.list("o", "r", "main", "src/a.txt", bob.getId()).comments().get(0).canEdit())
        .isFalse();
    assertThat(service.list("o", "r", "main", "src/a.txt", null).comments().get(0).canDelete())
        .isFalse();
  }

  @Test
  @DisplayName("after the file changes, old comments are flagged outdated and new ones are current")
  void outdatedAfterNewCommit() throws Exception {
    commit("a.txt", "one\n");
    service.create("o", "r", alice.getId(), form("a.txt", null, "File level"));
    commit("a.txt", "one\nmore\n");
    service.create("o", "r", bob.getId(), form("a.txt", 2, "On the new line"));

    final var page = service.list("o", "r", "main", "a.txt", alice.getId());

    assertThat(page.comments()).extracting(c -> c.outdated()).containsExactly(true, false);
  }

  @Test
  @DisplayName("missing files, folders, bad lines, binary files and bad paths are rejected")
  void validation() throws Exception {
    commit("src/a.txt", "one\ntwo\n");
    Files.write(dir.resolve("bin.dat"), new byte[] {0, 1, 2, 0, 3});
    git.add().addFilepattern(".").call();
    git.commit().setMessage("binary").setAuthor("a", "a@example.org").call();

    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("nope.txt", null, "x")))
        .isInstanceOf(ItemNotFoundException.class);
    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("src", null, "x")))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("src/a.txt", 9, "x")))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("bin.dat", 1, "x")))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () -> service.create("o", "r", alice.getId(), form("../etc/passwd", null, "x")))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("/abs", null, "x")))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(
            () ->
                service.create(
                    "o",
                    "r",
                    alice.getId(),
                    new FileCommentForm("ghost", "src/a.txt", null, "x", null)))
        .isInstanceOf(ItemNotFoundException.class);
    assertThat(service.create("o", "r", alice.getId(), form("bin.dat", null, "binary is fine")))
        .isNotNull();
  }

  @Test
  @DisplayName("only the author edits; the author or the repository owner deletes")
  void permissions() throws Exception {
    commit("a.txt", "one\n");
    final var created = service.create("o", "r", alice.getId(), form("a.txt", null, "mine"));

    assertThatThrownBy(() -> service.update("o", "r", created.id(), bob.getId(), "hijack"))
        .isInstanceOf(AccessNotAllowedException.class);
    assertThat(service.update("o", "r", created.id(), alice.getId(), "edited").updatedAt())
        .isNotNull();
    assertThatThrownBy(() -> service.delete("o", "r", created.id(), bob.getId()))
        .isInstanceOf(AccessNotAllowedException.class);

    service.delete("o", "r", created.id(), repoOwner.getId());
    verifyDeleted(created.id());
  }

  private void verifyDeleted(final UUID id) {
    org.mockito.Mockito.verify(comments)
        .delete(org.mockito.ArgumentMatchers.argThat(c -> c.getId().equals(id)));
  }

  @Test
  @DisplayName("disabled accounts cannot comment")
  void disabledAuthor() throws Exception {
    commit("a.txt", "one\n");
    alice.setEnabled(false);

    assertThatThrownBy(() -> service.create("o", "r", alice.getId(), form("a.txt", null, "x")))
        .isInstanceOf(AccessNotAllowedException.class);
  }
}
