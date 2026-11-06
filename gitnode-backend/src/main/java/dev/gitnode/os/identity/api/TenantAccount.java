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
package dev.gitnode.os.identity.api;

import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An account as seen by modules that work on the account itself (authentication, profile, admin).
 * The JPA entity stays internal to identity; these modules code against this view.
 */
@NullMarked
public interface TenantAccount extends AuthenticatedUser {

  void setUsername(String username);

  String getEmail();

  void setEmail(String email);

  @Nullable String getHash();

  void setHash(@Nullable String hash);

  @Nullable String getSalt();

  void setSalt(@Nullable String salt);

  @Nullable String getTotpSecret();

  void setTotpSecret(@Nullable String totpSecret);

  boolean isTotpEnabled();

  void setTotpEnabled(boolean totpEnabled);

  @Nullable String getTotpRecoveryCodes();

  void setTotpRecoveryCodes(@Nullable String totpRecoveryCodes);

  @Nullable Long getTotpLastStep();

  void setTotpLastStep(@Nullable Long totpLastStep);

  @Nullable String getDisplayName();

  void setDisplayName(@Nullable String displayName);

  @Nullable String getAvatarUrl();

  void setAvatarUrl(@Nullable String avatarUrl);

  @Nullable String getPasswordRecoveryCode();

  void setPasswordRecoveryCode(@Nullable String passwordRecoveryCode);

  @Nullable Instant getPasswordRecoveryExpiresAt();

  void setPasswordRecoveryExpiresAt(@Nullable Instant passwordRecoveryExpiresAt);

  @Nullable String getBio();

  void setBio(@Nullable String bio);

  @Nullable String getWebsite();

  void setWebsite(@Nullable String website);

  @Nullable String getLocation();

  void setLocation(@Nullable String location);

  @Nullable String getTimezone();

  void setTimezone(@Nullable String timezone);

  @Nullable String getProfileReadme();

  void setProfileReadme(@Nullable String profileReadme);

  boolean isEnabled();

  void setEnabled(boolean enabled);

  @Nullable Instant getCreatedAt();

  void setCreatedAt(@Nullable Instant createdAt);

  @Nullable Instant getUpdatedAt();
}
