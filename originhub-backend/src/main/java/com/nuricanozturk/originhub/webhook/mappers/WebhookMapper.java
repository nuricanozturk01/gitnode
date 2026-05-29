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
package com.nuricanozturk.originhub.webhook.mappers;

import com.nuricanozturk.originhub.webhook.dtos.WebhookInfo;
import com.nuricanozturk.originhub.webhook.entities.ProjectWebhook;
import com.nuricanozturk.originhub.webhook.entities.UserWebhook;
import com.nuricanozturk.originhub.webhook.entities.Webhook;
import java.util.HashSet;
import org.jspecify.annotations.NullMarked;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
@NullMarked
public interface WebhookMapper {

  default WebhookInfo toInfo(final Webhook webhook) {
    return WebhookInfo.builder()
        .id(webhook.getId())
        .url(webhook.getUrl())
        .enabled(webhook.isEnabled())
        .events(new HashSet<>(webhook.getSubscribedEvents()))
        .createdAt(webhook.getCreatedAt())
        .updatedAt(webhook.getUpdatedAt())
        .build();
  }

  default WebhookInfo toInfoFromUser(final UserWebhook webhook) {
    return WebhookInfo.builder()
        .id(webhook.getId())
        .url(webhook.getUrl())
        .enabled(webhook.isEnabled())
        .events(new HashSet<>(webhook.getSubscribedEvents()))
        .createdAt(webhook.getCreatedAt())
        .updatedAt(webhook.getUpdatedAt())
        .build();
  }

  default WebhookInfo toInfoFromProject(final ProjectWebhook webhook) {
    return WebhookInfo.builder()
        .id(webhook.getId())
        .url(webhook.getUrl())
        .enabled(webhook.isEnabled())
        .events(new HashSet<>(webhook.getSubscribedEvents()))
        .createdAt(webhook.getCreatedAt())
        .updatedAt(webhook.getUpdatedAt())
        .build();
  }
}
