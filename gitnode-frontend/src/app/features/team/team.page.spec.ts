///
/// Copyright 2026 the original author or authors.
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///      https://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LUCIDE_ICONS, LucideIconProvider } from 'lucide-angular';
import { LUCIDE_ICONS as ICON_OBJECT } from '../../lucide-icons';
import { ConfirmModalService } from '../../core/confirm-modal/confirm-modal.service';
import { CollaboratorService } from '../../core/collaborator/collaborator.service';
import { TeamService } from '../../core/team/team.service';
import { ToastService } from '../../core/toast/toast.service';
import type { TeamMember, TeamPage } from '../../domain/team/team.model';
import { TeamPage as TeamPageComponent } from './team.page';

function page(content: TeamMember[] = []): TeamPage {
  return { content, number: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

function member(id: string): TeamMember {
  return {
    id,
    owner: { id: 'o', username: 'boss', displayName: null, avatarUrl: null },
    member: { id: 'm', username: 'demo', displayName: null, avatarUrl: null },
    role: 'DEVELOPER',
    status: 'PENDING',
    createdAt: '',
    acceptedAt: null,
  };
}

describe('TeamPage', () => {
  let team: Record<string, jasmine.Spy>;

  async function create(): Promise<ComponentFixture<TeamPageComponent>> {
    const fixture = TestBed.createComponent(TeamPageComponent);
    await fixture.componentInstance.init();
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    team = {
      loadMe: jasmine.createSpy('loadMe').and.resolveTo(),
      members: jasmine.createSpy('members').and.resolveTo(page()),
      memberships: jasmine.createSpy('memberships').and.resolveTo(page()),
      myInvitations: jasmine.createSpy('myInvitations').and.resolveTo(page()),
      invite: jasmine.createSpy('invite').and.resolveTo(member('x')),
      changeRole: jasmine.createSpy('changeRole').and.resolveTo(member('x')),
      remove: jasmine.createSpy('remove').and.resolveTo(),
      cancelInvitation: jasmine.createSpy('cancelInvitation').and.resolveTo(),
      leave: jasmine.createSpy('leave').and.resolveTo(),
      accept: jasmine.createSpy('accept').and.resolveTo(member('x')),
      decline: jasmine.createSpy('decline').and.resolveTo(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider(ICON_OBJECT) },
        { provide: TeamService, useValue: team },
        { provide: CollaboratorService, useValue: { searchUsers: () => Promise.resolve([]) } },
        { provide: ConfirmModalService, useValue: { confirm: () => Promise.resolve(true) } },
        { provide: ToastService, useValue: { success: () => undefined, error: () => undefined } },
      ],
    });
  });

  it('showsAllThreeSectionsForEveryUser', async () => {
    const text = ((await create()).nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('My team');
    expect(text).toContain("Teams I'm in");
    expect(text).toContain('Invitations');
    expect(text).toContain('Send invitation');
  });

  it('inviteCallsServiceAndReloadsFirstPage', async () => {
    const cmp = (await create()).componentInstance;
    team['members'].calls.reset();
    cmp.inviteUsername.set('demo');
    cmp.inviteRole.set('MAINTAINER');
    await cmp.invite();
    expect(team['invite']).toHaveBeenCalledWith('demo', 'MAINTAINER');
    expect(team['members']).toHaveBeenCalledWith('ACCEPTED', 0, 20);
  });

  it('acceptRefreshesMeAndInvitations', async () => {
    team['myInvitations'].and.resolveTo(page([member('inv1')]));
    const cmp = (await create()).componentInstance;
    team['loadMe'].calls.reset();
    team['myInvitations'].calls.reset();
    await cmp.accept('inv1');
    expect(team['accept']).toHaveBeenCalledWith('inv1');
    expect(team['loadMe']).toHaveBeenCalled();
    expect(team['myInvitations']).toHaveBeenCalledWith(0, 20);
    expect(team['memberships']).toHaveBeenCalledWith(0, 20);
  });

  it('declineRefreshesInvitations', async () => {
    const cmp = (await create()).componentInstance;
    team['myInvitations'].calls.reset();
    await cmp.decline('inv1');
    expect(team['decline']).toHaveBeenCalledWith('inv1');
    expect(team['myInvitations']).toHaveBeenCalledWith(0, 20);
  });

  it('leaveCallsServiceWithMembershipId', async () => {
    team['memberships'].and.resolveTo(page([member('ms1')]));
    const cmp = (await create()).componentInstance;
    await cmp.leave('ms1', 'boss');
    expect(team['leave']).toHaveBeenCalledWith('ms1');
  });

  it('removingLastRowOfLastPageStepsBack', async () => {
    team['members'].and.resolveTo({ ...page([member('m1')]), number: 2, totalPages: 3 });
    const cmp = (await create()).componentInstance;
    cmp.memberIndex.set(2);
    team['members'].calls.reset();
    await cmp.remove('m1', 'demo');
    expect(team['remove']).toHaveBeenCalledWith('m1');
    expect(team['members']).toHaveBeenCalledWith('ACCEPTED', 1, 20);
  });

  it('cancelInvitationReloadsPendingFilter', async () => {
    const cmp = (await create()).componentInstance;
    cmp.statusFilter.set('PENDING');
    team['members'].calls.reset();
    await cmp.cancelInvitation('m1', 'demo');
    expect(team['cancelInvitation']).toHaveBeenCalledWith('m1');
    expect(team['members']).toHaveBeenCalledWith('PENDING', 0, 20);
  });
});
