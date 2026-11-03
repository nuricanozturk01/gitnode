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
package dev.gitnode.os.profile.services;

import dev.gitnode.os.identity.repositories.TenantRepository;
import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import dev.gitnode.os.shared.errorhandling.exceptions.ItemNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extra commit e-mail addresses of an account. Commits authored with one of them count toward the
 * account's contribution graph. Addresses are not verified (there is no mail delivery), so an
 * address that already belongs to another account, as primary or extra address, is refused.
 */
@Service
@RequiredArgsConstructor
@NullMarked
public class TenantEmailService {

  static final int MAX_EXTRA_EMAILS = 10;
  private static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern VALID =
      Pattern.compile("^[a-z0-9._%+'-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+$");
  private static final Pattern NOREPLY = Pattern.compile("@users\\.noreply\\.");

  private final NamedParameterJdbcTemplate jdbc;
  private final TenantRepository tenants;

  public record ExtraEmail(UUID id, String email, Instant createdAt) {}

  @Transactional(readOnly = true)
  public List<ExtraEmail> list(final UUID tenantId) {
    return this.jdbc.query(
        "SELECT id, email, created_at FROM tenant_emails WHERE tenant_id = :t ORDER BY created_at",
        new MapSqlParameterSource("t", tenantId),
        (rs, row) ->
            new ExtraEmail(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getTimestamp("created_at").toInstant()));
  }

  @Transactional
  public ExtraEmail add(final UUID tenantId, final String rawEmail) {
    final var email = rawEmail.strip().toLowerCase(Locale.ROOT);
    this.assertAddable(tenantId, email);
    final var id = UUID.randomUUID();
    try {
      this.jdbc.update(
          "INSERT INTO tenant_emails (id, tenant_id, email) VALUES (:id, :t, :email)",
          new MapSqlParameterSource()
              .addValue("id", id)
              .addValue("t", tenantId)
              .addValue("email", email));
    } catch (final org.springframework.dao.DuplicateKeyException e) {
      throw new BadRequestException("This e-mail address belongs to another account");
    }
    this.scheduleReindex();
    return new ExtraEmail(id, email, Instant.now());
  }

  private void assertAddable(final UUID tenantId, final String email) {
    if (email.length() > MAX_EMAIL_LENGTH
        || !VALID.matcher(email).matches()
        || NOREPLY.matcher(email).find()) {
      throw new BadRequestException("Enter a valid e-mail address");
    }
    final var tenant =
        this.tenants
            .findById(tenantId)
            .orElseThrow(() -> new ItemNotFoundException("userNotFound"));
    if (email.equalsIgnoreCase(tenant.getEmail())) {
      throw new BadRequestException("This is already your primary e-mail address");
    }
    if (!this.tenants.findAllByEmailIn(List.of(email)).isEmpty()) {
      throw new BadRequestException("This e-mail address belongs to another account");
    }
    if (this.list(tenantId).size() >= MAX_EXTRA_EMAILS) {
      throw new BadRequestException("You can add at most " + MAX_EXTRA_EMAILS + " extra e-mails");
    }
  }

  @Transactional
  public void remove(final UUID tenantId, final UUID emailId) {
    final var removed =
        this.jdbc.update(
            "DELETE FROM tenant_emails WHERE id = :id AND tenant_id = :t",
            new MapSqlParameterSource().addValue("id", emailId).addValue("t", tenantId));
    if (removed == 0) {
      throw new ItemNotFoundException("Email not found");
    }
  }

  /**
   * Earlier commits are attributed when a repository is indexed, so forgetting the indexed heads
   * makes the background backfill walk every repository again (duplicates are ignored).
   */
  private void scheduleReindex() {
    this.jdbc.getJdbcOperations().update("DELETE FROM commit_index_state");
  }
}
