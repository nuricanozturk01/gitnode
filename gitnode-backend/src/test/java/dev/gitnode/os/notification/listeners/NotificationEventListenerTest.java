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
package dev.gitnode.os.notification.listeners;

import static org.mockito.Mockito.verify;

import dev.gitnode.os.events.team.TeamInvitationClosedEvent;
import dev.gitnode.os.events.team.TeamInvitedEvent;
import dev.gitnode.os.notification.entities.NotificationType;
import dev.gitnode.os.notification.services.NotificationService;
import dev.gitnode.os.notification.services.NotificationSseRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener unit tests")
class NotificationEventListenerTest {

  @Mock private NotificationService notificationService;
  @Mock private NotificationSseRegistry sseRegistry;

  @InjectMocks private NotificationEventListener listener;

  @Test
  @DisplayName("onTeamInvited sends a TEAM_INVITED notification linking to the team page")
  void onTeamInvited_sendsTeamInvitedNotificationLinkingToTeamPage() {
    final var memberId = UUID.randomUUID();
    final var ownerId = UUID.randomUUID();
    final var invitationId = UUID.randomUUID();

    this.listener.onTeamInvited(new TeamInvitedEvent(memberId, ownerId, "DEVELOPER", invitationId));

    verify(this.notificationService)
        .send(
            memberId,
            ownerId,
            NotificationType.TEAM_INVITED,
            "You were invited to a team",
            "Role: DEVELOPER. Open Team to accept or decline.",
            "/team",
            invitationId);
  }

  @Test
  @DisplayName("onTeamInvitationClosed deletes the member's TEAM_INVITED notification")
  void onTeamInvitationClosed_deletesTeamInvitedNotification() {
    final var memberId = UUID.randomUUID();
    final var invitationId = UUID.randomUUID();

    this.listener.onTeamInvitationClosed(new TeamInvitationClosedEvent(invitationId, memberId));

    verify(this.notificationService)
        .deleteByEntity(NotificationType.TEAM_INVITED, memberId, invitationId);
  }

  @Test
  @DisplayName("onTenantDeleting deletes TEAM_INVITED notifications sent by the deleted owner")
  void onTenantDeleting_deletesTeamInvitedNotificationsByActor() {
    final var ownerId = UUID.randomUUID();

    this.listener.onTenantDeleting(new dev.gitnode.os.events.profile.TenantDeletingEvent(ownerId));

    verify(this.notificationService).deleteByActor(NotificationType.TEAM_INVITED, ownerId);
  }
}
