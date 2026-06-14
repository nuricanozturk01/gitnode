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
package dev.gitnode.os.notification.repositories;

import dev.gitnode.os.notification.entities.Notification;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@NullMarked
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

  long countByRecipientIdAndReadFalse(UUID recipientId);

  @Modifying
  @Query(
      "UPDATE Notification n SET n.read = true WHERE n.recipientId = :recipientId AND n.read = false")
  void markAllReadByRecipientId(@Param("recipientId") UUID recipientId);

  @Modifying
  @Query("DELETE FROM Notification n WHERE n.recipientId = :recipientId")
  void deleteAllByRecipientId(@Param("recipientId") UUID recipientId);

  boolean existsByIdAndRecipientId(UUID id, UUID recipientId);
}
