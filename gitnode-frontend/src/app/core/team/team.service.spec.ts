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

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TeamService } from './team.service';

describe('TeamService', () => {
  let service: TeamService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TeamService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loadMe_setsSignal', async () => {
    const promise = service.loadMe();
    const req = http.expectOne((r) => r.url.endsWith('/api/team/me'));
    req.flush({ pendingInvitations: 2 });
    await promise;
    expect(service.me()).toEqual({ pendingInvitations: 2 });
  });

  it('invite_postsUsernameAndRole', async () => {
    const promise = service.invite('demo', 'DEVELOPER');
    const req = http.expectOne((r) => r.url.endsWith('/api/team/invitations'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'demo', role: 'DEVELOPER' });
    req.flush({ id: '1' });
    await promise;
  });

  it('leave_deletesMembershipById', async () => {
    const promise = service.leave('m1');
    const req = http.expectOne((r) => r.url.endsWith('/api/team/memberships/m1'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await promise;
  });

  it('memberships_sendsPageAndSize', async () => {
    const promise = service.memberships(1, 20);
    const req = http.expectOne((r) => r.url.endsWith('/api/team/memberships'));
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], number: 1, size: 20, totalElements: 0, totalPages: 0 });
    await promise;
  });

  it('members_sendsStatusPageAndSize', async () => {
    const promise = service.members('PENDING', 2, 20);
    const req = http.expectOne((r) => r.url.endsWith('/api/team/members'));
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [], number: 2, size: 20, totalElements: 0, totalPages: 0 });
    await promise;
  });
});
