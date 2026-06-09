/**
 * Kanban task ↔ repository branch ↔ pull request integration (REST + commits + Git).
 * SCN-TASK-REPO-01 … SCN-TASK-COMMIT-01 — see RAPOR-SCENARIO.md
 */
import { E2E_PASSWORD } from '@helpers/test-user';

import { expect, test } from './fixtures/scenario';
import {
  gitCheckout,
  gitClone,
  httpGitRemoteUrl,
  removeWorkDir,
  scenarioWorkDir,
  writeFileAndPush,
} from './helpers/git';
import { createScenarioIssue, getLinkedTasksForIssue } from './helpers/issue-api';
import {
  closePullRequest,
  createPullRequest,
  listCommitMessages,
  mergePullRequest,
  prepareFeatureBranch,
  putBlob,
  waitForPullRequestStatus,
} from './helpers/scenario-api';
import {
  bootstrapScenarioProject,
  createBranchFromSubtask,
  createBranchFromTask,
  createScenarioSubtask,
  createScenarioTask,
  createScenarioTaskWithLinkedIssue,
  getProjectLinkedRepos,
  getTaskDetail,
  listCommitsOnBranch,
  patchProject,
  waitForSubtaskLinkedPr,
  waitForTaskCompletedAfterPrMerge,
  waitForTaskLinkedPr,
} from './helpers/task-api';

test.describe('SCN-TASK — task, repository, and pull request linkage', () => {
  // SCN-TASK-REPO-01 — Branch from task links repo + branch; git branch exists with commits.
  test('creating a branch from a task links the repository and creates git history', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'Branch linkage scenario');

    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );

    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'task-work.txt',
      'task branch content\n',
      'scenario: task branch commit',
    );

    const task = await getTaskDetail(bareApi, owner, project.projectCode, taskCode);
    expect(task.branchName).toBe(branchName);
    expect(task.branchRepoId).toBe(privateRepo.id);
    expect(task.status).toBe('IN_PROGRESS');

    const commitCount = await listCommitsOnBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
    );
    expect(commitCount).toBeGreaterThan(0);
  });

  // SCN-TASK-PR-01 — Opening a PR on the task branch sets linkedPr (PullRequestCreatedEvent).
  test('opening a pull request links the task by repo id and source branch', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'PR link scenario');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'pr-link.txt',
      'pr link\n',
      'scenario: pr link commit',
    );

    await createPullRequest(bareApi, owner.authorization, owner.username, privateRepo.name, {
      title: 'Task-linked PR',
      sourceBranch: branchName,
    });

    const task = await waitForTaskLinkedPr(
      bareApi,
      owner,
      project.projectCode,
      taskCode,
      branchName,
    );
    expect(task.linkedPr?.number).toBeGreaterThan(0);
    expect(task.linkedPr?.status).toBe('OPEN');
    expect(task.status).toBe('IN_PROGRESS');
  });

  // SCN-TASK-PR-02 — Merging the PR marks the task COMPLETED when syncTaskStatusOnPrMerge is on.
  test('merging a linked pull request completes the task', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'PR merge scenario');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'merge-complete.txt',
      'merge content\n',
      'scenario: merge task commit',
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Merge to complete task', sourceBranch: branchName, isDraft: false },
    );
    await waitForTaskLinkedPr(bareApi, owner, project.projectCode, taskCode, branchName);

    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitForPullRequestStatus(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
      'MERGED',
    );

    const task = await waitForTaskCompletedAfterPrMerge(
      bareApi,
      owner,
      project.projectCode,
      taskCode,
    );
    expect(task.status).toBe('COMPLETED');
    expect(task.linkedPr?.status).toBe('MERGED');
  });

  // SCN-TASK-PR-03 — Subtask branch + PR links linkedPr on the subtask (same repo/branch matching).
  test('opening a pull request links the subtask when branch was created from subtask', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'Subtask PR scenario');
    const subtaskId = await createScenarioSubtask(
      bareApi,
      owner,
      project.projectCode,
      taskCode,
      'Implementation subtask',
    );
    const branchName = await createBranchFromSubtask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      subtaskId,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'subtask-work.txt',
      'subtask branch\n',
      'scenario: subtask commit',
    );

    await createPullRequest(bareApi, owner.authorization, owner.username, privateRepo.name, {
      title: 'Subtask-linked PR',
      sourceBranch: branchName,
    });

    await waitForSubtaskLinkedPr(
      bareApi,
      owner,
      project.projectCode,
      taskCode,
      subtaskId,
      branchName,
    );

    const task = await getTaskDetail(bareApi, owner, project.projectCode, taskCode);
    const subtask = task.subtasks.find((s) => s.id === subtaskId);
    expect(subtask?.linkedPr?.sourceBranch).toBe(branchName);
    expect(subtask?.status).toBe('IN_PROGRESS');
  });

  // SCN-TASK-PR-04 — With syncTaskStatusOnPrMerge=false, merged PR does not complete the task.
  test('does not complete task on merge when project sync is disabled', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    await patchProject(bareApi, owner, project.projectCode, { syncTaskStatusOnPrMerge: false });

    const taskCode = await createScenarioTask(bareApi, owner, project, 'No sync on merge');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'no-sync.txt',
      'content\n',
      'scenario: no sync commit',
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'No task sync merge', sourceBranch: branchName, isDraft: false },
    );
    await waitForTaskLinkedPr(bareApi, owner, project.projectCode, taskCode, branchName);
    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitForPullRequestStatus(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
      'MERGED',
    );

    const task = await getTaskDetail(bareApi, owner, project.projectCode, taskCode);
    expect(task.linkedPr?.status).toBe('MERGED');
    expect(task.status).toBe('IN_PROGRESS');
  });

  // SCN-TASK-PR-05 — Closing a linked PR does not auto-complete the task.
  test('keeps task in progress when linked pull request is closed', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'PR close scenario');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'close-pr.txt',
      'content\n',
      'scenario: close pr commit',
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Close only', sourceBranch: branchName, isDraft: true },
    );
    await waitForTaskLinkedPr(bareApi, owner, project.projectCode, taskCode, branchName);
    await closePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    const task = await getTaskDetail(bareApi, owner, project.projectCode, taskCode);
    expect(task.status).toBe('IN_PROGRESS');
  });

  // SCN-TASK-ISSUE-01 — Task created with linkedIssueId appears in issue linked-tasks.
  test('links task to issue and exposes it via linked-tasks endpoint', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const issue = await createScenarioIssue(
      bareApi,
      owner,
      owner.username,
      privateRepo.name,
      'Task link issue',
    );
    const taskCode = await createScenarioTaskWithLinkedIssue(
      bareApi,
      owner,
      project,
      'Issue-linked task',
      issue.id,
    );

    const linked = await getLinkedTasksForIssue(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      issue.number,
    );
    expect(linked.some((t) => t.taskCode === taskCode)).toBe(true);
  });

  // SCN-TASK-PROJECT-01 — Project linked repos list includes open pull requests for that repo.
  test('lists open pull requests on project linked repository', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const branch = `scn-proj-pr-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );
    await createPullRequest(bareApi, owner.authorization, owner.username, privateRepo.name, {
      title: 'Project board PR',
      sourceBranch: branch,
    });

    const repos = await getProjectLinkedRepos(bareApi, owner, project.projectCode);
    const entry = repos.find((r) => r.id === privateRepo.id);
    expect(entry).toBeDefined();
    expect(entry?.openPullRequests.some((p) => p.title === 'Project board PR')).toBe(true);
  });

  // SCN-TASK-GIT-01 — HTTP Git push on task branch increases commit count.
  test('http git push on task branch adds commits visible via API', async ({
    bareApi,
    owner,
    privateRepo,
  }, testInfo) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'Git push scenario');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    const before = await listCommitsOnBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
    );

    const workDir = scenarioWorkDir(`${testInfo.testId}-git-push`);
    const remote = httpGitRemoteUrl(owner.username, privateRepo.name, {
      username: owner.username,
      password: E2E_PASSWORD,
    });
    try {
      gitClone(remote, workDir);
      gitCheckout(workDir, branchName);
      writeFileAndPush(
        workDir,
        'git-scenario.txt',
        'from git cli\n',
        'scenario: http git push',
        branchName,
      );
    } finally {
      removeWorkDir(workDir);
    }

    const after = await listCommitsOnBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
    );
    expect(after).toBeGreaterThan(before);
  });

  // SCN-TASK-COMMIT-01 — Task-driven PR merge leaves merge commit on main.
  test('task-linked pull request merge adds merge commit on main', async ({
    bareApi,
    owner,
    privateRepo,
  }) => {
    const project = await bootstrapScenarioProject(bareApi, owner, privateRepo);
    const taskCode = await createScenarioTask(bareApi, owner, project, 'Merge commit scenario');
    const branchName = await createBranchFromTask(
      bareApi,
      owner,
      owner.username,
      project.projectCode,
      taskCode,
      privateRepo,
    );
    await putBlob(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branchName,
      'merge-main.txt',
      'task merge\n',
      'scenario: task merge commit',
    );

    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Task merge to main', sourceBranch: branchName, isDraft: false },
    );
    await waitForTaskLinkedPr(bareApi, owner, project.projectCode, taskCode, branchName);
    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    const messages = await listCommitMessages(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      'main',
    );
    expect(messages.some((m) => m.toLowerCase().includes('merge'))).toBe(true);
    await waitForTaskCompletedAfterPrMerge(bareApi, owner, project.projectCode, taskCode);
  });
});
