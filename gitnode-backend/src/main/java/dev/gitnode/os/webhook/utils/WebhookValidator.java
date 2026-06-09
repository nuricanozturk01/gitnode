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
package dev.gitnode.os.webhook.utils;

import dev.gitnode.os.shared.errorhandling.exceptions.ErrorOccurredException;
import dev.gitnode.os.webhook.entities.WebhookEventType;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

@UtilityClass
@NullMarked
public class WebhookValidator {

  public static final int MAX_WEBHOOKS = 3;
  public static final String ERR_URL_EXISTS = "Webhook with this URL already exists";
  public static final String ERR_NOT_FOUND = "Webhook not found";
  public static final String ERR_NOT_AUTHORIZED = "notAuthorized";

  public static final Set<String> ALL_EVENTS =
      Arrays.stream(WebhookEventType.values())
          .map(Enum::name)
          .collect(Collectors.toUnmodifiableSet());

  public static void validateEvents(final Set<String> events, final Set<String> validEvents) {
    final var invalid = events.stream().filter(e -> !validEvents.contains(e)).toList();
    if (!invalid.isEmpty()) {
      throw new ErrorOccurredException("Invalid event types: " + invalid);
    }
  }
}
