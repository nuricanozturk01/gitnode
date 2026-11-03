package dev.gitnode.os.auth.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  private static final byte[] RFC_SECRET =
      "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  private static TotpService at(final long epochSeconds) {
    return new TotpService(
        "secret", Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
  }

  @Test
  void matchesRfc6238Vectors() {
    assertThat(TotpService.hotp(RFC_SECRET, 59 / 30)).isEqualTo("287082");
    assertThat(TotpService.hotp(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
    assertThat(TotpService.hotp(RFC_SECRET, 20000000000L / 30)).isEqualTo("353130");
  }

  @Test
  void acceptsCurrentAndAdjacentStepOnly() {
    final var secret = new Base32().encodeToString(RFC_SECRET);
    final var service = at(59);
    assertThat(service.verify(secret, "287082", null)).hasValue(1);
    assertThat(at(89).verify(secret, "287082", null)).hasValue(1);
    assertThat(at(149).verify(secret, "287082", null)).isEmpty();
  }

  @Test
  void rejectsReplayOfUsedStep() {
    final var secret = new Base32().encodeToString(RFC_SECRET);
    assertThat(at(59).verify(secret, "287082", 1L)).isEmpty();
  }

  @Test
  void rejectsMalformedCodes() {
    final var secret = new Base32().encodeToString(RFC_SECRET);
    assertThat(at(59).verify(secret, "28708", null)).isEmpty();
    assertThat(at(59).verify(secret, "abcdef", null)).isEmpty();
  }

  @Test
  void encryptionRoundTrips() {
    final var service = at(0);
    final var secret = service.newSecret();
    final var stored = service.encrypt(secret);
    assertThat(stored).isNotEqualTo(secret);
    assertThat(service.decrypt(stored)).isEqualTo(secret);
  }
}
