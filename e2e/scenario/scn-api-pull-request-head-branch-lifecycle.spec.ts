/**
 * PR head-branch cleanup after merge or close (driven by repository settings).
 * SCN-PR-DEL-01 … SCN-PR-DEL-03 — see RAPOR-SCENARIO.md
 */
import { test } from './fixtures/scenario';
import {
  branchExists,
  closePullRequest,
  createPullRequest,
  mergePullRequest,
  patchRepoSettings,
  prepareFeatureBranch,
} from './helpers/scenario-api';
import { waitUntil } from './helpers/wait';

test.describe('SCN-PR-DEL — PR head branch cleanup after merge/close', () => {
  // SCN-PR-DEL-01 — With deleteHeadBranchOnPrMerge=true, head branch is removed after merge.
  test('deletes head branch on merge when enabled', async ({ bareApi, owner, privateRepo }) => {
    await patchRepoSettings(bareApi, owner, owner.username, privateRepo.name, {
      name: privateRepo.name,
      deleteHeadBranchOnPrMerge: true,
      deleteHeadBranchOnPrClose: false,
    });

    const branch = `scn-merge-del-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );
    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Merge delete head', sourceBranch: branch },
    );
    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitUntil(
      async () =>
        !(await branchExists(
          bareApi,
          owner.authorization,
          owner.username,
          privateRepo.name,
          branch,
        )),
    );
  });

  // SCN-PR-DEL-02 — With deleteHeadBranchOnPrClose=true, head branch is removed after close.
  test('deletes head branch on close when enabled', async ({ bareApi, owner, privateRepo }) => {
    await patchRepoSettings(bareApi, owner, owner.username, privateRepo.name, {
      name: privateRepo.name,
      deleteHeadBranchOnPrMerge: false,
      deleteHeadBranchOnPrClose: true,
    });

    const branch = `scn-close-del-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );
    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Close delete head', sourceBranch: branch, isDraft: true },
    );
    await closePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitUntil(
      async () =>
        !(await branchExists(
          bareApi,
          owner.authorization,
          owner.username,
          privateRepo.name,
          branch,
        )),
    );
  });

  // SCN-PR-DEL-03 — With both flags false, head branch remains after merge.
  test('keeps head branch on merge when disabled', async ({ bareApi, owner, privateRepo }) => {
    await patchRepoSettings(bareApi, owner, owner.username, privateRepo.name, {
      name: privateRepo.name,
      deleteHeadBranchOnPrMerge: false,
      deleteHeadBranchOnPrClose: false,
    });

    const branch = `scn-keep-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );
    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Keep head branch', sourceBranch: branch },
    );
    await mergePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitUntil(async () =>
      branchExists(bareApi, owner.authorization, owner.username, privateRepo.name, branch),
    );
  });

  // SCN-PR-DEL-04 — With deleteHeadBranchOnPrClose=false, head branch remains after close.
  test('keeps head branch on close when disabled', async ({ bareApi, owner, privateRepo }) => {
    await patchRepoSettings(bareApi, owner, owner.username, privateRepo.name, {
      name: privateRepo.name,
      deleteHeadBranchOnPrMerge: false,
      deleteHeadBranchOnPrClose: false,
    });

    const branch = `scn-close-keep-${Date.now().toString(36)}`;
    await prepareFeatureBranch(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      branch,
    );
    const pr = await createPullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      { title: 'Close keep head', sourceBranch: branch, isDraft: true },
    );
    await closePullRequest(
      bareApi,
      owner.authorization,
      owner.username,
      privateRepo.name,
      pr.number,
    );

    await waitUntil(async () =>
      branchExists(bareApi, owner.authorization, owner.username, privateRepo.name, branch),
    );
  });
});
