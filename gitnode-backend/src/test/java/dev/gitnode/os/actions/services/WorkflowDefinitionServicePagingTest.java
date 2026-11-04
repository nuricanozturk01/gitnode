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
package dev.gitnode.os.actions.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.gitnode.os.actions.model.WorkflowModel;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.actions.repositories.WorkflowRunRepository;
import dev.gitnode.os.actions.services.WorkflowParserService.ParsedWorkflow;
import dev.gitnode.os.repo.api.RepoLookupPort;
import dev.gitnode.os.repo.api.RepoLookupPort.RepoRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowDefinitionService: paged workflow list")
class WorkflowDefinitionServicePagingTest {

  private static final UUID REPO_ID = UUID.randomUUID();

  @Mock private WorkflowParserService parserService;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private RepoLookupPort repoLookup;

  private WorkflowDefinitionService service;

  @BeforeEach
  void setUp() {
    this.service =
        new WorkflowDefinitionService(
            this.parserService, this.definitionRepository, this.runRepository, this.repoLookup);
    when(this.repoLookup.findById(REPO_ID))
        .thenReturn(
            Optional.of(new RepoRef(REPO_ID, UUID.randomUUID(), "alice", "app", false, "main")));
  }

  private void workflows(final String... names) {
    final var parsed = new ArrayList<ParsedWorkflow>();
    for (final var name : names) {
      parsed.add(
          new ParsedWorkflow(
              ".gitnode/workflows/" + name + ".yml",
              "",
              new WorkflowModel(name, null, null, null, null)));
    }
    when(this.parserService.parseWorkflows("alice", "app", "refs/heads/main")).thenReturn(parsed);
    when(this.definitionRepository.findAllByRepoIdOrderByFilePathAsc(REPO_ID))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName("pages are cut from the name-ordered list with correct totals")
  void slicesPages() {
    this.workflows("delta", "Alpha", "charlie", "bravo", "echo");

    final var first = this.service.pageForRepo(REPO_ID, null, PageRequest.of(0, 2));
    final var last = this.service.pageForRepo(REPO_ID, null, PageRequest.of(2, 2));

    assertThat(first.getContent()).extracting("name").containsExactly("Alpha", "bravo");
    assertThat(first.getTotalElements()).isEqualTo(5);
    assertThat(first.getTotalPages()).isEqualTo(3);
    assertThat(last.getContent()).extracting("name").containsExactly("echo");
  }

  @Test
  @DisplayName("a page past the end is empty, not an error")
  void pageBeyondTheEnd() {
    this.workflows("a", "b");

    final var page = this.service.pageForRepo(REPO_ID, null, PageRequest.of(7, 20));

    assertThat(page.getContent()).isEmpty();
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  @DisplayName("the query filters by name or file path, case-insensitively")
  void filtersByQuery() {
    this.workflows("build", "deploy", "Release");

    assertThat(this.service.pageForRepo(REPO_ID, "REL", PageRequest.of(0, 20)).getContent())
        .extracting("name")
        .containsExactly("Release");
    assertThat(
            this.service.pageForRepo(REPO_ID, "workflows/dep", PageRequest.of(0, 20)).getContent())
        .extracting("name")
        .containsExactly("deploy");
    assertThat(this.service.pageForRepo(REPO_ID, "   ", PageRequest.of(0, 20)).getTotalElements())
        .isEqualTo(3);
    assertThat(
            this.service.pageForRepo(REPO_ID, "nothing", PageRequest.of(0, 20)).getTotalElements())
        .isZero();
  }

  @Test
  @DisplayName("one page never holds more than the maximum page size")
  void sizeIsCapped() {
    final var names = new String[150];
    for (int i = 0; i < names.length; i++) {
      names[i] = String.format("wf%03d", i);
    }
    this.workflows(names);

    final var page = this.service.pageForRepo(REPO_ID, null, PageRequest.of(0, 500));

    assertThat(page.getContent()).hasSize(WorkflowDefinitionService.MAX_PAGE_SIZE);
    assertThat(page.getSize()).isEqualTo(WorkflowDefinitionService.MAX_PAGE_SIZE);
    assertThat(page.getTotalPages()).isEqualTo(2);
  }
}
