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

import dev.gitnode.os.actions.entities.WorkflowDefinition;
import dev.gitnode.os.actions.entities.WorkflowRun;
import dev.gitnode.os.actions.repositories.WorkflowDefinitionRepository;
import dev.gitnode.os.repo.api.RepoLookupPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("A run keeps the workflow file it was queued with")
class RunWorkflowSnapshotTest {

  private static final String QUEUED_WITH =
      """
      name: queued
      on: workflow_dispatch
      jobs:
        old:
          runs-on: [self-hosted]
          steps:
            - run: echo old
      """;

  private static final String EDITED_LATER =
      """
      name: edited
      on: workflow_dispatch
      jobs:
        new:
          runs-on: [self-hosted]
          steps:
            - run: echo new
      """;

  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private RepoLookupPort repoLookup;

  private JobAdvancer advancer;
  private final WorkflowParserService parser = new WorkflowParserService();

  @BeforeEach
  void setUp() {
    this.advancer =
        new JobAdvancer(
            this.definitionRepository, this.parser, new ExpressionEvaluator(), this.repoLookup);
  }

  private WorkflowRun run(final String snapshot) {
    final var run = new WorkflowRun();
    run.setId(UUID.randomUUID());
    run.setWorkflowDefId(UUID.randomUUID());
    run.setWorkflowContent(snapshot);
    return run;
  }

  @Test
  @DisplayName("the snapshot wins over a definition that was edited afterwards")
  void snapshotWinsOverEditedDefinition() {
    final var run = this.run(QUEUED_WITH);
    final var definition = new WorkflowDefinition();
    definition.setContent(EDITED_LATER);
    org.mockito.Mockito.lenient()
        .when(this.definitionRepository.findById(run.getWorkflowDefId()))
        .thenReturn(Optional.of(definition));

    final var model = this.advancer.loadModel(run);

    assertThat(model).isNotNull();
    assertThat(model.jobs()).containsOnlyKeys("old");
  }

  @Test
  @DisplayName("runs from before the snapshot existed fall back to the definition")
  void fallsBackToDefinition() {
    final var run = this.run(null);
    final var definition = new WorkflowDefinition();
    definition.setContent(EDITED_LATER);
    when(this.definitionRepository.findById(run.getWorkflowDefId()))
        .thenReturn(Optional.of(definition));

    assertThat(this.advancer.loadModel(run).jobs()).containsOnlyKeys("new");
  }

  @Test
  @DisplayName("without a snapshot and without a definition there is no model")
  void noModelAtAll() {
    final var run = this.run(null);
    run.setWorkflowDefId(null);

    assertThat(this.advancer.loadModel(run)).isNull();
  }
}
