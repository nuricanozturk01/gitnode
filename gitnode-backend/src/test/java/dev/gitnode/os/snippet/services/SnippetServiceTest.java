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
package dev.gitnode.os.snippet.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.shared.cache.SnippetCacheInvalidator;
import dev.gitnode.os.shared.errorhandling.exceptions.AccessNotAllowedException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import dev.gitnode.os.shared.repo.dtos.PageResponse;
import dev.gitnode.os.shared.repo.repositories.RepoRepository;
import dev.gitnode.os.shared.tenant.entities.Tenant;
import dev.gitnode.os.shared.tenant.repositories.TenantRepository;
import dev.gitnode.os.snippet.dtos.SnippetDetail;
import dev.gitnode.os.snippet.dtos.SnippetFileForm;
import dev.gitnode.os.snippet.dtos.SnippetForm;
import dev.gitnode.os.snippet.dtos.SnippetInfo;
import dev.gitnode.os.snippet.dtos.SnippetOwnerInfo;
import dev.gitnode.os.snippet.dtos.SnippetRevisionInfo;
import dev.gitnode.os.snippet.dtos.SnippetUpdateForm;
import dev.gitnode.os.snippet.entities.Snippet;
import dev.gitnode.os.snippet.entities.SnippetFile;
import dev.gitnode.os.snippet.entities.SnippetRevision;
import dev.gitnode.os.snippet.entities.Visibility;
import dev.gitnode.os.snippet.mappers.SnippetMapper;
import dev.gitnode.os.snippet.repositories.SnippetRepository;
import dev.gitnode.os.snippet.repositories.SnippetRevisionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("SnippetService unit tests")
class SnippetServiceTest {

  @Mock private SnippetRepository snippetRepository;
  @Mock private SnippetRevisionRepository revisionRepository;
  @Mock private TenantRepository tenantRepository;
  @Mock private SnippetMapper snippetMapper;
  @Mock private SnippetFileStorageService fileStorage;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SnippetCacheInvalidator snippetCacheInvalidator;
  @Mock private RepoRepository repoRepository;

  @InjectMocks private SnippetService snippetService;

  // ──────────────────────────── helpers ────────────────────────────

  private Tenant tenant(UUID id, String username) {
    Tenant t = new Tenant();
    t.setId(id);
    t.setUsername(username);
    return t;
  }

  private Snippet publicSnippet(UUID id, Tenant owner, String title) {
    Snippet s = new Snippet();
    s.setId(id);
    s.setOwner(owner);
    s.setTitle(title);
    s.setVisibility(Visibility.PUBLIC);
    s.setFileCount(0);
    s.setFiles(new ArrayList<>());
    return s;
  }

  private Snippet privateSnippet(UUID id, Tenant owner, String title) {
    Snippet s = publicSnippet(id, owner, title);
    s.setVisibility(Visibility.PRIVATE);
    return s;
  }

  private SnippetFile file(UUID id, UUID snippetId, String filename) {
    SnippetFile f = new SnippetFile();
    f.setId(id);
    f.setFilename(filename);
    f.setPosition(0);
    return f;
  }

  private SnippetDetail stubDetail(UUID id, String title) {
    SnippetOwnerInfo owner =
        SnippetOwnerInfo.builder().id(UUID.randomUUID()).username("owner").build();
    return SnippetDetail.builder()
        .id(id)
        .title(title)
        .visibility(Visibility.PUBLIC)
        .owner(owner)
        .files(List.of())
        .fileCount(0)
        .commentCount(0)
        .forkCount(0)
        .build();
  }

  private SnippetInfo stubInfo(UUID id, String title) {
    SnippetOwnerInfo owner =
        SnippetOwnerInfo.builder().id(UUID.randomUUID()).username("owner").build();
    return SnippetInfo.builder()
        .id(id)
        .title(title)
        .visibility(Visibility.PUBLIC)
        .owner(owner)
        .fileCount(0)
        .commentCount(0)
        .forkCount(0)
        .build();
  }

  // ──────────────────────────── create ────────────────────────────

  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("throws ItemNotFoundException when tenant not found")
    void throws_whenTenantMissing() {
      UUID tenantId = UUID.randomUUID();
      SnippetForm form =
          new SnippetForm(
              "title",
              null,
              Visibility.PUBLIC,
              List.of(new SnippetFileForm("main.py", "print(1)")));
      when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> snippetService.create(tenantId, form))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("userNotFound");
    }

    @Test
    @DisplayName("saves snippet, writes files to disk, creates initial revision")
    void savesSnippetAndWritesFiles() {
      UUID tenantId = UUID.randomUUID();
      Tenant owner = tenant(tenantId, "alice");
      SnippetFileForm fileForm = new SnippetFileForm("hello.py", "print('hi')");
      SnippetForm form = new SnippetForm("My Snippet", null, Visibility.PUBLIC, List.of(fileForm));

      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();
      Snippet saved = publicSnippet(snippetId, owner, "My Snippet");
      SnippetFile sf = file(fileId, snippetId, "hello.py");
      saved.getFiles().add(sf);
      sf.setSnippet(saved);
      saved.setFileCount(1);

      SnippetDetail expectedDetail = stubDetail(snippetId, "My Snippet");

      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
      when(snippetRepository.save(any(Snippet.class))).thenReturn(saved);
      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
      when(revisionRepository.save(any(SnippetRevision.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      when(snippetMapper.toDetail(eq(saved), any())).thenReturn(expectedDetail);

      SnippetDetail result = snippetService.create(tenantId, form);

      assertThat(result).isSameAs(expectedDetail);
      verify(snippetRepository).save(any(Snippet.class));
      verify(fileStorage).writeFile(eq("alice"), eq(snippetId), eq(fileId), eq("print('hi')"));
      verify(revisionRepository).save(any(SnippetRevision.class));
    }

    @Test
    @DisplayName("sets fileCount equal to number of files in form")
    void setsFileCount() {
      UUID tenantId = UUID.randomUUID();
      Tenant owner = tenant(tenantId, "alice");
      List<SnippetFileForm> fileForms =
          List.of(new SnippetFileForm("a.py", "a"), new SnippetFileForm("b.py", "b"));
      SnippetForm form = new SnippetForm("Snippet", null, Visibility.PUBLIC, fileForms);

      Snippet saved = publicSnippet(UUID.randomUUID(), owner, "Snippet");
      saved.setFileCount(2);

      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
      when(snippetRepository.save(any()))
          .thenAnswer(
              inv -> {
                Snippet s = inv.getArgument(0);
                assertThat(s.getFileCount()).isEqualTo(2);
                return saved;
              });
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(snippetMapper.toDetail(any(), any())).thenReturn(stubDetail(saved.getId(), "Snippet"));

      snippetService.create(tenantId, form);
    }
  }

  // ──────────────────────────── get ────────────────────────────

  @Nested
  @DisplayName("get()")
  class Get {

    @Test
    @DisplayName("returns detail for public snippet with null callerId")
    void returnsDetail_forPublicSnippet_anonymousCaller() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "bob");
      Snippet snippet = publicSnippet(snippetId, owner, "Public");
      SnippetDetail expected = stubDetail(snippetId, "Public");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(snippetMapper.toDetail(any(), any())).thenReturn(expected);

      SnippetDetail result = snippetService.get(snippetId, null);

      assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("throws ItemNotFoundException for private snippet with null callerId")
    void throws_forPrivateSnippet_anonymousCaller() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "bob");
      Snippet snippet = privateSnippet(snippetId, owner, "Secret");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.get(snippetId, null))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("throws ItemNotFoundException for private snippet when caller is not owner")
    void throws_forPrivateSnippet_differentCaller() {
      UUID snippetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "bob");
      Snippet snippet = privateSnippet(snippetId, owner, "Secret");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.get(snippetId, otherId))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("returns detail for private snippet when caller is the owner")
    void returnsDetail_forPrivateSnippet_ownerCaller() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "bob");
      Snippet snippet = privateSnippet(snippetId, owner, "Secret");
      SnippetDetail expected = stubDetail(snippetId, "Secret");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(snippetMapper.toDetail(any(), any())).thenReturn(expected);

      SnippetDetail result = snippetService.get(snippetId, ownerId);

      assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("throws ItemNotFoundException when snippet does not exist")
    void throws_whenSnippetMissing() {
      UUID snippetId = UUID.randomUUID();
      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> snippetService.get(snippetId, null))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }
  }

  // ──────────────────────────── update ────────────────────────────

  @Nested
  @DisplayName("update()")
  class Update {

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is not owner")
    void throws_whenCallerIsNotOwner() {
      UUID snippetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "owner");
      Snippet snippet = publicSnippet(snippetId, owner, "title");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.update(otherId, snippetId, new SnippetUpdateForm()))
          .isInstanceOf(AccessNotAllowedException.class)
          .hasMessageContaining("notSnippetOwner");
    }

    @Test
    @DisplayName("updates title and description without changing files")
    void updatesMetadata_whenNoFilesProvided() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet snippet = publicSnippet(snippetId, owner, "Old Title");
      SnippetFile existingFile = file(UUID.randomUUID(), snippetId, "main.py");
      snippet.getFiles().add(existingFile);
      snippet.setFileCount(1);

      SnippetUpdateForm form = new SnippetUpdateForm();
      form.setTitle("New Title");
      form.setDescription("updated desc");

      SnippetDetail expected = stubDetail(snippetId, "New Title");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(snippetRepository.save(snippet)).thenReturn(snippet);
      when(tenantRepository.findById(ownerId)).thenReturn(Optional.of(owner));
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(fileStorage.readFile(any(), any(), any())).thenReturn("print(1)");
      when(snippetMapper.toDetail(any(), any())).thenReturn(expected);

      SnippetDetail result = snippetService.update(ownerId, snippetId, form);

      assertThat(result).isSameAs(expected);
      assertThat(snippet.getTitle()).isEqualTo("New Title");
      assertThat(snippet.getDescription()).isEqualTo("updated desc");
      verify(fileStorage, never()).deleteCurrentFiles(any(), any());
    }

    @Test
    @DisplayName("replaces files on disk and creates new revision when files provided")
    void replacesFiles_whenFilesProvided() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet snippet = publicSnippet(snippetId, owner, "title");

      SnippetUpdateForm form = new SnippetUpdateForm();
      form.setFiles(List.of(new SnippetFileForm("new.ts", "const x = 1;")));
      form.setSummary("added ts file");

      UUID newFileId = UUID.randomUUID();
      SnippetFile newFile = file(newFileId, snippetId, "new.ts");
      Snippet saved = publicSnippet(snippetId, owner, "title");
      saved.getFiles().add(newFile);
      newFile.setSnippet(saved);
      saved.setFileCount(1);

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(snippetRepository.save(snippet)).thenReturn(saved);
      when(tenantRepository.findById(ownerId)).thenReturn(Optional.of(owner));
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(snippetMapper.toDetail(any(), any())).thenReturn(stubDetail(snippetId, "title"));

      snippetService.update(ownerId, snippetId, form);

      verify(fileStorage).deleteCurrentFiles("alice", snippetId);
      verify(fileStorage).writeFile("alice", snippetId, newFileId, "const x = 1;");

      ArgumentCaptor<SnippetRevision> revCaptor = ArgumentCaptor.forClass(SnippetRevision.class);
      verify(revisionRepository).save(revCaptor.capture());
      assertThat(revCaptor.getValue().getSummary()).isEqualTo("added ts file");
    }
  }

  // ──────────────────────────── delete ────────────────────────────

  @Nested
  @DisplayName("delete()")
  class Delete {

    @Test
    @DisplayName("throws AccessNotAllowedException when caller is not owner")
    void throws_whenCallerIsNotOwner() {
      UUID snippetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID otherId = UUID.randomUUID();
      Snippet snippet = publicSnippet(snippetId, tenant(ownerId, "owner"), "title");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.delete(otherId, snippetId))
          .isInstanceOf(AccessNotAllowedException.class)
          .hasMessageContaining("notSnippetOwner");
    }

    @Test
    @DisplayName("deletes snippet from DB and removes disk directory")
    void deletesSnippetAndDiskDir() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet snippet = publicSnippet(snippetId, owner, "title");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      snippetService.delete(ownerId, snippetId);

      verify(snippetRepository).delete(snippet);
      verify(fileStorage).deleteSnippetDir("alice", snippetId);
    }
  }

  // ──────────────────────────── fork ────────────────────────────

  @Nested
  @DisplayName("fork()")
  class Fork {

    @Test
    @DisplayName("throws ItemNotFoundException when forking a private snippet as non-owner")
    void throws_whenForkingPrivateSnippet_asNonOwner() {
      UUID snippetId = UUID.randomUUID();
      UUID ownerId = UUID.randomUUID();
      UUID forkerId = UUID.randomUUID();
      Snippet snippet = privateSnippet(snippetId, tenant(ownerId, "owner"), "secret");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.fork(forkerId, snippetId))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("creates fork with cloned files and increments original forkCount")
    void createsForkAndIncrementsForkCount() {
      UUID originalOwnerId = UUID.randomUUID();
      UUID forkerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      UUID fileId = UUID.randomUUID();

      Tenant originalOwner = tenant(originalOwnerId, "alice");
      Tenant forker = tenant(forkerId, "bob");

      Snippet original = publicSnippet(snippetId, originalOwner, "Awesome Snippet");
      SnippetFile originalFile = file(fileId, snippetId, "main.go");
      originalFile.setSnippet(original);
      original.getFiles().add(originalFile);
      original.setFileCount(1);

      UUID forkedId = UUID.randomUUID();
      Snippet savedFork = publicSnippet(forkedId, forker, "Awesome Snippet");
      UUID forkedFileId = UUID.randomUUID();
      SnippetFile forkedFile = file(forkedFileId, forkedId, "main.go");
      forkedFile.setSnippet(savedFork);
      savedFork.getFiles().add(forkedFile);
      savedFork.setFileCount(1);

      SnippetDetail expectedDetail = stubDetail(forkedId, "Awesome Snippet");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(original));
      when(tenantRepository.findById(forkerId)).thenReturn(Optional.of(forker));
      when(snippetRepository.save(any())).thenReturn(savedFork);
      when(tenantRepository.findById(forkerId)).thenReturn(Optional.of(forker));
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(fileStorage.readFile("alice", snippetId, fileId)).thenReturn("package main");
      when(snippetMapper.toDetail(any(), any())).thenReturn(expectedDetail);

      SnippetDetail result = snippetService.fork(forkerId, snippetId);

      assertThat(result).isSameAs(expectedDetail);
      verify(snippetRepository).incrementForkCount(snippetId);
      verify(fileStorage).writeFile("bob", forkedId, forkedFileId, "package main");

      ArgumentCaptor<SnippetRevision> revCaptor = ArgumentCaptor.forClass(SnippetRevision.class);
      verify(revisionRepository).save(revCaptor.capture());
      assertThat(revCaptor.getValue().getSummary()).contains("Forked from");
    }

    @Test
    @DisplayName("fork of private snippet succeeds when caller is the owner")
    void forkPrivateSnippet_succeedsForOwner() {
      UUID ownerId = UUID.randomUUID();
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet original = privateSnippet(snippetId, owner, "Private Snippet");

      Snippet savedFork = publicSnippet(UUID.randomUUID(), owner, "Private Snippet");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(original));
      when(tenantRepository.findById(ownerId)).thenReturn(Optional.of(owner));
      when(snippetRepository.save(any())).thenReturn(savedFork);
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(snippetMapper.toDetail(any(), any()))
          .thenReturn(stubDetail(savedFork.getId(), "Private Snippet"));

      SnippetDetail result = snippetService.fork(ownerId, snippetId);

      assertThat(result).isNotNull();
    }
  }

  // ──────────────────────────── listPublic ────────────────────────────

  @Nested
  @DisplayName("listPublic()")
  class ListPublic {

    @Test
    @DisplayName("returns paged public snippets without search term")
    void returnsPagedPublicSnippets() {
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet s1 = publicSnippet(UUID.randomUUID(), owner, "A");
      Snippet s2 = publicSnippet(UUID.randomUUID(), owner, "B");
      SnippetInfo i1 = stubInfo(s1.getId(), "A");
      SnippetInfo i2 = stubInfo(s2.getId(), "B");

      when(snippetRepository.findAllByVisibility(eq(Visibility.PUBLIC), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(s1, s2)));
      when(snippetMapper.toInfo(s1)).thenReturn(i1);
      when(snippetMapper.toInfo(s2)).thenReturn(i2);

      var result = snippetService.listPublic(0, 20, null);

      assertThat(result.getContent()).containsExactly(i1, i2);
    }

    @Test
    @DisplayName("uses search query when q is provided")
    void usesSearch_whenQProvided() {
      Tenant owner = tenant(UUID.randomUUID(), "owner");
      Snippet s = publicSnippet(UUID.randomUUID(), owner, "Java Tips");
      SnippetInfo info = stubInfo(s.getId(), "Java Tips");

      when(snippetRepository.searchPublic(eq("java"), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(s)));
      when(snippetMapper.toInfo(s)).thenReturn(info);

      var result = snippetService.listPublic(0, 20, "java");

      assertThat(result.getContent()).singleElement().isSameAs(info);
      verify(snippetRepository, never()).findAllByVisibility(any(), any());
    }
  }

  // ──────────────────────────── listMine ────────────────────────────

  @Nested
  @DisplayName("listMine()")
  class ListMine {

    @Test
    @DisplayName("returns all snippets for the owner including private ones")
    void returnsAllSnippets_includingPrivate() {
      UUID ownerId = UUID.randomUUID();
      Tenant owner = tenant(ownerId, "alice");
      Snippet pub = publicSnippet(UUID.randomUUID(), owner, "Public");
      Snippet priv = privateSnippet(UUID.randomUUID(), owner, "Private");
      SnippetInfo pubInfo = stubInfo(pub.getId(), "Public");
      SnippetInfo privInfo = stubInfo(priv.getId(), "Private");

      when(snippetRepository.findAllByOwnerIdOrderByCreatedAtDesc(eq(ownerId), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(pub, priv)));
      when(snippetMapper.toInfo(pub)).thenReturn(pubInfo);
      when(snippetMapper.toInfo(priv)).thenReturn(privInfo);

      var result = snippetService.listMine(ownerId, 0, 20);

      assertThat(result.content()).containsExactly(pubInfo, privInfo);
    }
  }

  // ──────────────────────────── listRevisions ────────────────────────────

  @Nested
  @DisplayName("listRevisions()")
  class ListRevisions {

    @Test
    @DisplayName("throws ItemNotFoundException for private snippet when caller is anonymous")
    void throws_forPrivateSnippet_anonymousCaller() {
      UUID snippetId = UUID.randomUUID();
      Snippet snippet = privateSnippet(snippetId, tenant(UUID.randomUUID(), "alice"), "secret");

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));

      assertThatThrownBy(() -> snippetService.listRevisions(snippetId, null, 0, 10))
          .isInstanceOf(ItemNotFoundException.class)
          .hasMessageContaining("snippetNotFound");
    }

    @Test
    @DisplayName("returns paged revisions for public snippet")
    void returnsPagedRevisions_forPublicSnippet() {
      UUID snippetId = UUID.randomUUID();
      Tenant owner = tenant(UUID.randomUUID(), "alice");
      Snippet snippet = publicSnippet(snippetId, owner, "Snippet");

      SnippetRevision rev = new SnippetRevision();
      rev.setId(UUID.randomUUID());
      rev.setSnippet(snippet);
      rev.setAuthor(owner);
      rev.setCreatedAt(Instant.now());

      SnippetRevisionInfo revInfo =
          SnippetRevisionInfo.builder()
              .id(rev.getId())
              .author(SnippetOwnerInfo.builder().id(owner.getId()).username("alice").build())
              .build();

      when(snippetRepository.findByIdWithOwner(snippetId)).thenReturn(Optional.of(snippet));
      when(revisionRepository.findAllBySnippetIdOrderByCreatedAtDesc(
              eq(snippetId), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(rev)));
      when(snippetMapper.toRevisionInfo(rev)).thenReturn(revInfo);

      PageResponse<SnippetRevisionInfo> result =
          snippetService.listRevisions(snippetId, null, 0, 10);

      assertThat(result.content()).singleElement().isSameAs(revInfo);
    }
  }

  // ──────────────────────────── revision snapshot ────────────────────────────

  @Nested
  @DisplayName("revision snapshot (via create)")
  class RevisionSnapshot {

    @Test
    @DisplayName("each create produces exactly one initial revision with summary=null")
    void createProducesOneRevisionWithNullSummary() {
      UUID tenantId = UUID.randomUUID();
      Tenant owner = tenant(tenantId, "alice");
      SnippetForm form =
          new SnippetForm("s", null, Visibility.PUBLIC, List.of(new SnippetFileForm("f.py", "x")));

      Snippet saved = publicSnippet(UUID.randomUUID(), owner, "s");
      saved.setFileCount(1);

      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(owner));
      when(snippetRepository.save(any())).thenReturn(saved);
      when(revisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(snippetMapper.toDetail(any(), any())).thenReturn(stubDetail(saved.getId(), "s"));

      snippetService.create(tenantId, form);

      ArgumentCaptor<SnippetRevision> captor = ArgumentCaptor.forClass(SnippetRevision.class);
      verify(revisionRepository, times(1)).save(captor.capture());
      assertThat(captor.getValue().getSummary()).isNull();
      assertThat(captor.getValue().getTitle()).isEqualTo("s");
    }
  }
}
