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
package dev.gitnode.os.admin.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.gitnode.os.webhook.api.WebhookDlqAdminPort;
import dev.gitnode.os.webhook.api.WebhookDlqEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminWebhookDlqController unit tests")
class AdminWebhookDlqControllerTest {

  @Mock private WebhookDlqAdminPort webhookDlqAdminPort;

  @InjectMocks private AdminWebhookDlqController adminWebhookDlqController;

  private static WebhookDlqEntry entry() {
    return new WebhookDlqEntry(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "https://example.com/hook",
        "PUSH",
        "timeout",
        3,
        1,
        null,
        Instant.EPOCH);
  }

  @Nested
  @DisplayName("GET /api/admin/webhooks/dlq")
  class ListDlq {

    @Test
    @DisplayName("returns paginated dead-letter entries sorted by failedAt desc")
    void list_returnsPage() {
      when(webhookDlqAdminPort.list(any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(entry())));

      var response = adminWebhookDlqController.list(0, 20);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().content()).hasSize(1);
      verify(webhookDlqAdminPort)
          .list(
              argThat(
                  pageable ->
                      pageable.getPageSize() == 20
                          && pageable.getSort().equals(Sort.by(Sort.Direction.DESC, "failedAt"))));
    }

    @Test
    @DisplayName("caps page size at 100")
    void list_capsPageSize() {
      when(webhookDlqAdminPort.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

      adminWebhookDlqController.list(0, 500);

      verify(webhookDlqAdminPort).list(argThat(pageable -> pageable.getPageSize() == 100));
    }
  }

  @Nested
  @DisplayName("GET /api/admin/webhooks/dlq/summary")
  class Summary {

    @Test
    @DisplayName("returns pending count")
    void summary_returnsPendingCount() {
      when(webhookDlqAdminPort.countPending()).thenReturn(7L);

      var response = adminWebhookDlqController.summary();

      assertThat(response.getBody()).containsEntry("pending", 7L);
    }
  }

  @Nested
  @DisplayName("POST /api/admin/webhooks/dlq/{id}/retry")
  class Retry {

    @Test
    @DisplayName("retries entry and returns 204")
    void retry_returnsNoContent() {
      var id = UUID.randomUUID();

      var response = adminWebhookDlqController.retry(id);

      verify(webhookDlqAdminPort).retry(id);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }

  @Nested
  @DisplayName("DELETE /api/admin/webhooks/dlq/{id}")
  class Dismiss {

    @Test
    @DisplayName("dismisses entry and returns 204")
    void dismiss_returnsNoContent() {
      var id = UUID.randomUUID();

      var response = adminWebhookDlqController.dismiss(id);

      verify(webhookDlqAdminPort).dismiss(id);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }
}
