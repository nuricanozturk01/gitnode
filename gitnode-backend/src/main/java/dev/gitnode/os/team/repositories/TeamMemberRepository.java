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
package dev.gitnode.os.team.repositories;

import dev.gitnode.os.team.api.TeamRole;
import dev.gitnode.os.team.entities.TeamMember;
import dev.gitnode.os.team.entities.TeamMemberStatus;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

  @Query(
      "SELECT m.role FROM TeamMember m "
          + "WHERE m.memberId = :memberId AND m.ownerId = :ownerId AND m.status = "
          + "dev.gitnode.os.team.entities.TeamMemberStatus.ACCEPTED")
  Optional<TeamRole> findAcceptedRole(
      @Param("memberId") UUID memberId, @Param("ownerId") UUID ownerId);

  Optional<TeamMember> findByOwnerIdAndMemberId(UUID ownerId, UUID memberId);

  Page<TeamMember> findAllByOwnerIdAndStatus(
      UUID ownerId, TeamMemberStatus status, Pageable pageable);

  Page<TeamMember> findAllByMemberIdAndStatus(
      UUID memberId, TeamMemberStatus status, Pageable pageable);

  long countByMemberIdAndStatus(UUID memberId, TeamMemberStatus status);
}
