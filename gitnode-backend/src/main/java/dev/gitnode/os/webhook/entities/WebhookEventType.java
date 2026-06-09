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
package dev.gitnode.os.webhook.entities;

public enum WebhookEventType {
  REPO_CREATED("repo.created"),
  REPO_DELETED("repo.deleted"),
  REPO_UPDATED("repo.updated"),
  REPO_PUSHED("repo.pushed"),
  BRANCH_CREATED("branch.created"),
  BRANCH_DELETED("branch.deleted"),
  PULL_REQUEST_OPENED("pull_request.opened"),
  PULL_REQUEST_CLOSED("pull_request.closed"),
  PULL_REQUEST_MERGED("pull_request.merged"),
  PULL_REQUEST_UPDATED("pull_request.updated"),
  ISSUE_OPENED("issue.opened"),
  ISSUE_CLOSED("issue.closed"),
  ISSUE_REOPENED("issue.reopened"),
  ISSUE_UPDATED("issue.updated"),
  ISSUE_COMMENTED("issue.commented"),
  PROJECT_CREATED("project.created"),
  PROJECT_DELETED("project.deleted"),
  PROJECT_UPDATED("project.updated"),
  TASK_CREATED("task.created"),
  TASK_DELETED("task.deleted"),
  TASK_UPDATED("task.updated"),
  SNIPPET_CREATED("snippet.created"),
  SNIPPET_DELETED("snippet.deleted"),
  SNIPPET_UPDATED("snippet.updated"),
  WORKFLOW_RUN_COMPLETED("workflow_run.completed");

  private final String value;

  WebhookEventType(final String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }
}
