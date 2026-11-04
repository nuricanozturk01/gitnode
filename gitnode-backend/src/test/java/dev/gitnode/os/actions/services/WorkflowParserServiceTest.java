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

import dev.gitnode.os.actions.model.WorkflowModel;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowParserService — YAML parsing unit tests")
class WorkflowParserServiceTest {

  private WorkflowModel parse(final String yaml) throws IOException {
    return WorkflowParserService.YAML_MAPPER.readValue(yaml, WorkflowModel.class);
  }

  @Test
  @DisplayName("parses workflow name and basic push trigger")
  void parse_basicPushTrigger() throws IOException {
    final var yaml =
        """
        name: CI
        "on":
          push:
            branches: [main, develop]
        jobs:
          build:
            runs-on: [self-hosted, linux]
            steps:
              - name: Hello
                run: echo hi
        """;

    final var model = this.parse(yaml);

    assertThat(model.name()).isEqualTo("CI");
    assertThat(model.on()).isNotNull();
    assertThat(model.on().push()).isNotNull();
    assertThat(model.on().push().branches()).containsExactly("main", "develop");
    assertThat(model.jobs()).containsKey("build");
    assertThat(model.jobs().get("build").runsOn()).containsExactly("self-hosted", "linux");
  }

  @Test
  @DisplayName("parses runs-on as a single string (not a list)")
  void parse_runsOnString() throws IOException {
    final var yaml =
        """
        name: CI
        "on":
          push: {}
        jobs:
          test:
            runs-on: self-hosted
            steps:
              - run: mvn test
        """;

    final var model = this.parse(yaml);

    assertThat(model.jobs().get("test").runsOn()).containsExactly("self-hosted");
  }

  @Test
  @DisplayName("parses needs dependency list")
  void parse_needsDependency() throws IOException {
    final var yaml =
        """
        name: Deploy
        "on":
          push: {}
        jobs:
          build:
            runs-on: [self-hosted]
            steps: []
          deploy:
            runs-on: [self-hosted]
            needs: [build]
            steps: []
        """;

    final var model = this.parse(yaml);

    assertThat(model.jobs().get("deploy").needs()).containsExactly("build");
    assertThat(model.jobs().get("build").needs()).isNullOrEmpty();
  }

  @Test
  @DisplayName("parses pull_request trigger")
  void parse_pullRequestTrigger() throws IOException {
    final var yaml =
        """
        name: PR Check
        "on":
          pull_request:
            branches: [main]
            types: [opened, synchronize]
        jobs:
          check:
            runs-on: [self-hosted]
            steps: []
        """;

    final var model = this.parse(yaml);

    assertThat(model.on().pullRequest()).isNotNull();
    assertThat(model.on().pullRequest().branches()).containsExactly("main");
    assertThat(model.on().pullRequest().types()).containsExactly("opened", "synchronize");
  }

  @Test
  @DisplayName("ignores unknown YAML fields without throwing")
  void parse_unknownFieldsIgnored() throws IOException {
    final var yaml =
        """
        name: Future
        "on":
          push: {}
        unknown_future_key: some_value
        jobs:
          test:
            runs-on: [self-hosted]
            unknown_job_field: value
            steps: []
        """;

    final var model = this.parse(yaml);

    assertThat(model.name()).isEqualTo("Future");
  }

  @Test
  @DisplayName("parses step with uses and with inputs")
  void parse_stepWithUses() throws IOException {
    final var yaml =
        """
        name: Build
        "on":
          push: {}
        jobs:
          build:
            runs-on: [self-hosted]
            steps:
              - uses: actions/checkout@v1
              - uses: pnpm/action-setup@v1
                with:
                  version: "9"
              - name: Install
                run: pnpm install
        """;

    final var model = this.parse(yaml);
    final var steps = model.jobs().get("build").steps();

    assertThat(steps).hasSize(3);
    assertThat(steps.get(0).uses()).isEqualTo("actions/checkout@v1");
    assertThat(steps.get(1).with()).containsEntry("version", "9");
    assertThat(steps.get(2).run()).isEqualTo("pnpm install");
  }

  @Test
  @DisplayName("parses branches-ignore list")
  void parse_branchesIgnore() throws IOException {
    final var yaml =
        """
        name: Skip Draft
        "on":
          push:
            branches-ignore: [dependabot/**, release/**]
        jobs:
          test:
            runs-on: [self-hosted]
            steps: []
        """;

    final var model = this.parse(yaml);

    assertThat(model.on().push().branchesIgnore()).containsExactly("dependabot/**", "release/**");
  }

  @Test
  @DisplayName("on: accepts a single event name")
  void on_singleString() throws IOException {
    final var model = this.parse("on: push\njobs:\n  a:\n    runs-on: x\n    steps: []\n");
    assertThat(model.on()).isNotNull();
    assertThat(model.on().push()).isNotNull();
    assertThat(model.on().pullRequest()).isNull();
  }

  @Test
  @DisplayName("on: accepts a list of event names")
  void on_list() throws IOException {
    final var model = this.parse("on: [push, pull_request, workflow_dispatch]\njobs: {}\n");
    assertThat(model.on().push()).isNotNull();
    assertThat(model.on().pullRequest()).isNotNull();
    assertThat(model.on().workflowDispatch()).isNotNull();
    assertThat(model.on().schedule()).isNull();
  }

  @Test
  @DisplayName("on: events without a value are enabled (workflow_dispatch:, push:)")
  void on_eventsWithoutValue() throws IOException {
    final var model = this.parse("on:\n  workflow_dispatch:\n  push:\n  pull_request:\njobs: {}\n");
    assertThat(model.on().workflowDispatch()).isNotNull();
    assertThat(model.on().workflowDispatch().inputs()).isNull();
    assertThat(model.on().push()).isNotNull();
    assertThat(model.on().pullRequest()).isNotNull();
  }

  @Test
  @DisplayName("on: absent events stay disabled")
  void on_absentEventsDisabled() throws IOException {
    final var model = this.parse("on:\n  push:\n    branches: [main]\njobs: {}\n");
    assertThat(model.on().push().branches()).containsExactly("main");
    assertThat(model.on().pullRequest()).isNull();
    assertThat(model.on().workflowDispatch()).isNull();
  }

  @Test
  @DisplayName("on: parses schedule entries with optional timezone")
  void on_schedule() throws IOException {
    final var model =
        this.parse(
            """
            on:
              schedule:
                - cron: "0 16 * * *"
                  timezone: 'Europe/Istanbul'
                - cron: "*/5 * * * *"
            jobs: {}
            """);
    assertThat(model.on().schedule()).hasSize(2);
    assertThat(model.on().schedule().get(0).cron()).isEqualTo("0 16 * * *");
    assertThat(model.on().schedule().get(0).timezone()).isEqualTo("Europe/Istanbul");
    assertThat(model.on().schedule().get(1).timezone()).isNull();
  }

  @Test
  @DisplayName("parses defaults and step-level shell, working-directory, continue-on-error")
  void defaultsAndStepFields() throws IOException {
    final var model =
        this.parse(
            """
            on: push
            defaults:
              run:
                shell: bash
                working-directory: app
            jobs:
              a:
                runs-on: x
                defaults:
                  run:
                    working-directory: web
                steps:
                  - id: s1
                    run: echo hi
                    shell: sh
                    working-directory: sub
                    continue-on-error: true
                    timeout-minutes: 3
            """);
    assertThat(model.defaults().run().shell()).isEqualTo("bash");
    assertThat(model.defaults().run().workingDirectory()).isEqualTo("app");
    final var job = model.jobs().get("a");
    assertThat(job.defaults().run().workingDirectory()).isEqualTo("web");
    final var step = job.steps().get(0);
    assertThat(step.shell()).isEqualTo("sh");
    assertThat(step.workingDirectory()).isEqualTo("sub");
    assertThat(step.continueOnError()).isEqualTo("true");
    assertThat(step.timeoutMinutes()).isEqualTo(3);
  }
}
