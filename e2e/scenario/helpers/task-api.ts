import {
  projectApi,
  projectBoardsApi,
  projectsApi,
  projectTasksApi,
  repoCommitsApi,
} from '@helpers/paths';
import type { APIRequestContext } from '@playwright/test';

import {
  uniqueProjectCodePrefix,
  uniqueScenarioBoardName,
  uniqueScenarioColumnName,
  uniqueScenarioProjectName,
} from '../../helpers/unique-id';
import type { ScenarioRepo, ScenarioUser } from './types';
import { waitUntil } from './wait';

export interface ScenarioProjectBoard {
  projectId: string;
  projectCode: string;
  boardId: string;
  columnId: string;
}

export interface TaskDetailResponse {
  code: string;
  status: string;
  branchName: string | null;
  branchRepoId: string | null;
  linkedPr: {
    id: string;
    number: number;
    sourceBranch: string;
    status: string;
  } | null;
  subtasks: {
    id: string;
    code: string;
    status: string;
    branchName: string | null;
    linkedPr: TaskDetailResponse['linkedPr'];
  }[];
}

export async function bootstrapScenarioProject(
  request: APIRequestContext,
  user: ScenarioUser,
  repo: ScenarioRepo,
): Promise<ScenarioProjectBoard> {
  const codePrefix = uniqueProjectCodePrefix();
  const projectName = uniqueScenarioProjectName();

  const projectResponse = await request.post(projectsApi(user.username), {
    headers: { Authorization: user.authorization },
    data: {
      name: projectName,
      description: 'E2E task scenario',
      codePrefix,
      isPublic: true,
    },
  });
  if (!projectResponse.ok()) {
    throw new Error(
      `create project failed (${projectResponse.status()}): ${await projectResponse.text()}`,
    );
  }
  const project = (await projectResponse.json()) as { id: string; codePrefix: string };
  const projectCode = project.codePrefix;

  const linkResponse = await request.post(
    `${projectApi(user.username, projectCode)}/repos/${repo.id}`,
    { headers: { Authorization: user.authorization } },
  );
  if (!linkResponse.ok()) {
    throw new Error(
      `link repo to project failed (${linkResponse.status()}): ${await linkResponse.text()}`,
    );
  }

  const boardResponse = await request.post(projectBoardsApi(user.username, projectCode), {
    headers: { Authorization: user.authorization },
    data: { name: uniqueScenarioBoardName(), position: 0 },
  });
  if (!boardResponse.ok()) {
    throw new Error(
      `create board failed (${boardResponse.status()}): ${await boardResponse.text()}`,
    );
  }
  const boardId = (await boardResponse.json()).id as string;

  const columnResponse = await request.post(
    `${projectBoardsApi(user.username, projectCode)}/${boardId}/columns`,
    {
      headers: { Authorization: user.authorization },
      data: { name: uniqueScenarioColumnName(), position: 0, color: '#64748b' },
    },
  );
  if (!columnResponse.ok()) {
    throw new Error(
      `create column failed (${columnResponse.status()}): ${await columnResponse.text()}`,
    );
  }
  const columnId = (await columnResponse.json()).id as string;

  return { projectId: project.id, projectCode, boardId, columnId };
}

export async function patchProject(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  data: { syncTaskStatusOnPrMerge?: boolean; name?: string },
): Promise<void> {
  const response = await request.patch(projectApi(user.username, projectCode), {
    headers: { Authorization: user.authorization },
    data,
  });
  if (!response.ok()) {
    throw new Error(`patch project failed (${response.status()}): ${await response.text()}`);
  }
}

export async function createScenarioTaskWithLinkedIssue(
  request: APIRequestContext,
  user: ScenarioUser,
  project: ScenarioProjectBoard,
  title: string,
  linkedIssueId: string,
): Promise<string> {
  const response = await request.post(projectTasksApi(user.username, project.projectCode), {
    headers: { Authorization: user.authorization },
    data: {
      title,
      description: 'Scenario task with issue',
      boardColumnId: project.columnId,
      type: 'TASK',
      position: 0,
      linkedIssueId,
    },
  });
  if (!response.ok()) {
    throw new Error(
      `create task with issue failed (${response.status()}): ${await response.text()}`,
    );
  }
  return (await response.json()).code as string;
}

export interface ProjectRepoLinkInfo {
  id: string;
  name: string;
  openPullRequests: { number: number; title: string; sourceBranch: string }[];
}

export async function getProjectLinkedRepos(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
): Promise<ProjectRepoLinkInfo[]> {
  const response = await request.get(`${projectApi(user.username, projectCode)}/repos`, {
    headers: { Authorization: user.authorization },
  });
  if (!response.ok()) {
    throw new Error(`get project repos failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as ProjectRepoLinkInfo[];
}

export async function createScenarioTask(
  request: APIRequestContext,
  user: ScenarioUser,
  project: ScenarioProjectBoard,
  title: string,
): Promise<string> {
  const response = await request.post(projectTasksApi(user.username, project.projectCode), {
    headers: { Authorization: user.authorization },
    data: {
      title,
      description: 'Scenario task',
      boardColumnId: project.columnId,
      type: 'TASK',
      position: 0,
    },
  });
  if (!response.ok()) {
    throw new Error(`create task failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()).code as string;
}

export async function createBranchFromTask(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  projectCode: string,
  taskCode: string,
  repo: ScenarioRepo,
  sourceBranch = 'main',
): Promise<string> {
  const response = await request.post(`${projectTasksApi(owner, projectCode)}/${taskCode}/branch`, {
    headers: { Authorization: user.authorization },
    data: {
      repoOwner: owner,
      repoName: repo.name,
      sourceBranch,
    },
  });
  if (!response.ok()) {
    throw new Error(
      `create branch from task failed (${response.status()}): ${await response.text()}`,
    );
  }
  return (await response.json()).name as string;
}

export async function createBranchFromSubtask(
  request: APIRequestContext,
  user: ScenarioUser,
  owner: string,
  projectCode: string,
  taskCode: string,
  subtaskId: string,
  repo: ScenarioRepo,
  sourceBranch = 'main',
): Promise<string> {
  const response = await request.post(
    `${projectTasksApi(owner, projectCode)}/${taskCode}/subtasks/${subtaskId}/branch`,
    {
      headers: { Authorization: user.authorization },
      data: {
        repoOwner: owner,
        repoName: repo.name,
        sourceBranch,
      },
    },
  );
  if (!response.ok()) {
    throw new Error(
      `create branch from subtask failed (${response.status()}): ${await response.text()}`,
    );
  }
  return (await response.json()).name as string;
}

export async function createScenarioSubtask(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
  title: string,
): Promise<string> {
  const response = await request.post(
    `${projectTasksApi(user.username, projectCode)}/${taskCode}/subtasks`,
    {
      headers: { Authorization: user.authorization },
      data: { title, position: 0 },
    },
  );
  if (!response.ok()) {
    throw new Error(`create subtask failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()).id as string;
}

export async function getTaskDetail(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
): Promise<TaskDetailResponse> {
  const response = await request.get(`${projectTasksApi(user.username, projectCode)}/${taskCode}`, {
    headers: { Authorization: user.authorization },
  });
  if (!response.ok()) {
    throw new Error(`get task failed (${response.status()}): ${await response.text()}`);
  }
  return (await response.json()) as TaskDetailResponse;
}

export async function waitForTaskLinkedPr(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
  sourceBranch: string,
): Promise<TaskDetailResponse> {
  let detail!: TaskDetailResponse;
  await waitUntil(async () => {
    detail = await getTaskDetail(request, user, projectCode, taskCode);
    const pr = detail.linkedPr;
    return pr != null && pr.number > 0 && pr.sourceBranch === sourceBranch && pr.status === 'OPEN';
  });
  return detail;
}

/** After PR merge: task COMPLETED (sync on) and linked PR MERGED (async Modulith listeners). */
export async function waitForTaskCompletedAfterPrMerge(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
): Promise<TaskDetailResponse> {
  let detail!: TaskDetailResponse;
  await waitUntil(async () => {
    detail = await getTaskDetail(request, user, projectCode, taskCode);
    return detail.status === 'COMPLETED' && detail.linkedPr?.status === 'MERGED';
  });
  return detail;
}

export async function waitForTaskStatus(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
  status: string,
): Promise<TaskDetailResponse> {
  let detail!: TaskDetailResponse;
  await waitUntil(async () => {
    detail = await getTaskDetail(request, user, projectCode, taskCode);
    return detail.status === status;
  });
  return detail;
}

export async function waitForSubtaskLinkedPr(
  request: APIRequestContext,
  user: ScenarioUser,
  projectCode: string,
  taskCode: string,
  subtaskId: string,
  sourceBranch: string,
): Promise<void> {
  await waitUntil(async () => {
    const detail = await getTaskDetail(request, user, projectCode, taskCode);
    const subtask = detail.subtasks.find((s) => s.id === subtaskId);
    return subtask?.linkedPr?.sourceBranch === sourceBranch;
  });
}

export async function listCommitsOnBranch(
  request: APIRequestContext,
  authorization: string,
  owner: string,
  repo: string,
  branch: string,
): Promise<number> {
  const response = await request.get(repoCommitsApi(owner, repo), {
    headers: { Authorization: authorization },
    params: { branch, page: '0', size: '10' },
  });
  if (!response.ok()) {
    throw new Error(`list commits failed (${response.status()}): ${await response.text()}`);
  }
  const body = (await response.json()) as { items: unknown[] };
  return body.items.length;
}
