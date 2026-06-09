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
package dev.gitnode.os.webhook.repositories;

import dev.gitnode.os.webhook.entities.UserWebhook;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface UserWebhookRepository extends JpaRepository<UserWebhook, UUID> {

  List<UserWebhook> findAllByUserId(UUID userId);

  List<UserWebhook> findAllByUserIdAndEnabledTrue(UUID userId);

  int countByUserId(UUID userId);

  boolean existsByUserIdAndUrl(UUID userId, String url);
}
