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

package dev.gitnode.os.tag.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.git.provider.GitProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ReleaseNotesService with a real Git repository")
class ReleaseNotesServiceTest {

  @TempDir Path dir;

  private Git git;
  private ReleaseNotesService service;

  @BeforeEach
  void setUp() throws Exception {
    git = Git.init().setInitialBranch("main").setDirectory(dir.toFile()).call();
    final var provider = mock(GitProvider.class);
    when(provider.open("o", "r")).thenAnswer(i -> Git.open(dir.toFile()).getRepository());
    service = new ReleaseNotesService(provider);
  }

  private void commit(final String file, final String subject) throws Exception {
    Files.writeString(dir.resolve(file), subject);
    git.add().addFilepattern(".").call();
    git.commit().setMessage(subject).setAuthor("Alice", "a@example.org").call();
  }

  @Test
  @DisplayName("lists only the commits since the previous tag, newest first")
  void commitsSincePreviousTag() throws Exception {
    commit("a", "Initial import");
    git.tag().setName("v1.0").call();
    commit("b", "Add search");
    commit("c", "Fix crash on empty repo");

    final var notes = service.generate("o", "r", "v1.1", "main", null);

    assertThat(notes.name()).isEqualTo("v1.1");
    assertThat(notes.previousTag()).isEqualTo("v1.0");
    assertThat(notes.commitCount()).isEqualTo(2);
    assertThat(notes.body())
        .startsWith("## What's Changed")
        .contains("* Fix crash on empty repo (`")
        .contains("by Alice")
        .contains("* Add search")
        .doesNotContain("Initial import")
        .contains("**Full Changelog**: `v1.0...v1.1`");
    assertThat(notes.body().indexOf("Fix crash")).isLessThan(notes.body().indexOf("Add search"));
  }

  @Test
  @DisplayName("the first release lists everything and has no changelog link")
  void firstRelease() throws Exception {
    commit("a", "Initial import");
    commit("b", "Add search");

    final var notes = service.generate("o", "r", "v1.0", "main", null);

    assertThat(notes.previousTag()).isNull();
    assertThat(notes.commitCount()).isEqualTo(2);
    assertThat(notes.body()).doesNotContain("Full Changelog");
  }

  @Test
  @DisplayName("an existing tag and an explicit previous tag are honoured")
  void explicitPreviousTag() throws Exception {
    commit("a", "one");
    git.tag().setName("v1").call();
    commit("b", "two");
    git.tag().setName("v2").call();
    commit("c", "three");
    git.tag().setName("v3").call();

    final var notes = service.generate("o", "r", "v3", null, "v1");

    assertThat(notes.commitCount()).isEqualTo(2);
    assertThat(notes.body()).contains("* three").contains("* two").doesNotContain("* one");
  }

  @Test
  @DisplayName("a second tag on the same commit reports no changes")
  void noChanges() throws Exception {
    commit("a", "one");
    git.tag().setName("v1").call();
    git.tag().setName("v1-copy").call();

    final var notes = service.generate("o", "r", "v1-copy", null, null);

    assertThat(notes.previousTag()).isEqualTo("v1");
    assertThat(notes.commitCount()).isZero();
    assertThat(notes.body()).contains("No changes");
  }

  @Test
  @DisplayName("missing tags, targets and invalid names are rejected")
  void invalidInput() throws Exception {
    commit("a", "one");

    assertThatThrownBy(() -> service.generate("o", "r", "v9", null, null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.generate("o", "r", "v9", "no-such-branch", null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.generate("o", "r", "bad..name", "main", null))
        .isInstanceOf(BadRequestException.class);
    assertThatThrownBy(() -> service.generate("o", "r", "v9", "main", "missing"))
        .isInstanceOf(BadRequestException.class);
  }
}
