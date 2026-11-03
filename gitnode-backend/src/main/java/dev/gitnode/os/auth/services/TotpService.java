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
package dev.gitnode.os.auth.services;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** RFC 6238 time-based one-time passwords (HMAC-SHA1, 6 digits, 30 s) and secret encryption. */
@Component
@NullMarked
public class TotpService {

  static final int STEP_SECONDS = 30;
  private static final int DIGITS = 6;
  private static final int SECRET_BYTES = 20;
  private static final int WINDOW = 1;
  private static final int GCM_IV = 12;
  private static final int GCM_TAG_BITS = 128;
  private static final int HOTP_BYTE_3 = 3;
  private static final String ISSUER = "GitNode";

  private final SecureRandom random = new SecureRandom();
  private final Clock clock;
  private final SecretKeySpec key;

  @org.springframework.beans.factory.annotation.Autowired
  public TotpService(@Value("${gitnode.jwt.secret}") final String jwtSecret) {
    this(jwtSecret, Clock.systemUTC());
  }

  TotpService(final String jwtSecret, final Clock clock) {
    this.clock = clock;
    try {
      final var digest =
          MessageDigest.getInstance("SHA-256")
              .digest(("gitnode-2fa:" + jwtSecret).getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (final GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String newSecret() {
    final var bytes = new byte[SECRET_BYTES];
    this.random.nextBytes(bytes);
    return new Base32().encodeToString(bytes).replace("=", "");
  }

  public String otpAuthUri(final String account, final String secret) {
    final var label = enc(ISSUER) + ":" + enc(account);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + enc(ISSUER)
        + "&algorithm=SHA1&digits="
        + DIGITS
        + "&period="
        + STEP_SECONDS;
  }

  /**
   * Checks a code against the current step and its neighbours. Returns the matched time step, or
   * empty when no step matches or the step is not newer than {@code lastUsedStep} (replay).
   */
  public OptionalLong verify(final String secret, final String code, final Long lastUsedStep) {
    if (code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
      return OptionalLong.empty();
    }
    final var base32 = new Base32().decode(secret);
    final var now = this.clock.millis() / 1000 / STEP_SECONDS;
    var matched = OptionalLong.empty();
    for (long step = now - WINDOW; step <= now + WINDOW; step++) {
      final var ok =
          MessageDigest.isEqual(
              hotp(base32, step).getBytes(StandardCharsets.UTF_8),
              code.getBytes(StandardCharsets.UTF_8));
      if (ok && (lastUsedStep == null || step > lastUsedStep)) {
        matched = OptionalLong.of(step);
      }
    }
    return matched;
  }

  static String hotp(final byte[] secret, final long counter) {
    try {
      final var mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      final var hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
      final var offset = hash[hash.length - 1] & 0x0f;
      final var binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + HOTP_BYTE_3] & 0xff);
      final var otp = binary % (int) Math.pow(10, DIGITS);
      return String.format("%0" + DIGITS + "d", otp);
    } catch (final GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String encrypt(final String plain) {
    try {
      final var iv = new byte[GCM_IV];
      this.random.nextBytes(iv);
      final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      final var enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
    } catch (final GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public String decrypt(final String stored) {
    try {
      final var all = Base64.getDecoder().decode(stored);
      final var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV));
      return new String(cipher.doFinal(all, GCM_IV, all.length - GCM_IV), StandardCharsets.UTF_8);
    } catch (final GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String enc(final String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
