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
package com.nuricanozturk.originhub.ssh.services;

import com.nuricanozturk.originhub.shared.repo.repositories.RepoRepository;
import com.nuricanozturk.originhub.shared.tenant.entities.Tenant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.sshd.common.AttributeRepository;
import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OriginHubSshServer {

  private static final byte EC_POINT_UNCOMPRESSED_PREFIX = 0x04;
  private static final int EC_FIELD_SIZE_NIST_P256 = 256;
  private static final int EC_FIELD_SIZE_NIST_P384 = 384;
  private static final int EC_FIELD_SIZE_NIST_P521 = 521;
  private static final AttributeRepository.AttributeKey<Tenant> TENANT_KEY =
      new AttributeRepository.AttributeKey<>();

  private final @NonNull SshKeyService sshKeyService;
  private final @NonNull RepoRepository repoRepository;

  @Value("${originhub.ssh.port:2222}")
  private int sshPort;

  @Value("${originhub.git.repo-root}")
  private String repoRoot;

  @Value("${originhub.ssh.host-key-path}")
  private String hostKeyPath;

  @Value("${originhub.ssh.host-path}")
  private String hostPath;

  private SshServer sshServer;

  @PostConstruct
  public void start() throws IOException {

    Files.createDirectories(Path.of(this.hostPath));

    final var keyProvider = this.createKeyProvider();

    this.sshServer = SshServer.setUpDefaultServer();
    this.sshServer.setPort(this.sshPort);
    this.sshServer.setKeyPairProvider(keyProvider);
    this.sshServer.setPublickeyAuthenticator(this.buildPublicKeyAuthenticator());
    this.sshServer.setCommandFactory(this.buildCommandFactory());
    this.sshServer.start();

    log.warn("OriginHub SSH server started on port {}", this.sshPort);
  }

  @PreDestroy
  public void stop() throws IOException {

    if (this.sshServer != null && this.sshServer.isStarted()) {
      this.sshServer.stop();
      log.warn("OriginHub SSH server stopped");
    }
  }

  private @NonNull PublickeyAuthenticator buildPublicKeyAuthenticator() {
    return (username, key, session) -> {
      try {
        final var wireBytes = toSshWireFormat(key);
        final var fingerprint = SshKeyService.fingerprintFromWireBytes(wireBytes);
        final var tenant = this.sshKeyService.findTenantByFingerprint(fingerprint);

        session.setAttribute(TENANT_KEY, tenant);

        log.info("SSH auth success: username={}, tenant={}", username, tenant.getUsername());
        return true;
      } catch (final Exception ex) {
        log.warn("SSH auth failed: username={}, reason={}", username, ex.getMessage());
        return false;
      }
    };
  }

  private static byte @NonNull [] toSshWireFormat(final @NonNull PublicKey key) throws IOException {

    final var baos = new ByteArrayOutputStream();

    try (final var dos = new DataOutputStream(baos)) {

      switch (key) {
        case final RSAPublicKey rsa -> {
          writeString(dos, "ssh-rsa");
          writeBytes(dos, rsa.getPublicExponent().toByteArray());
          writeBytes(dos, rsa.getModulus().toByteArray());
        }
        case final EdDSAPublicKey ed -> {
          writeString(dos, "ssh-ed25519");
          writeBytes(dos, ed.getAbyte());
        }
        case final ECPublicKey ec -> {
          final var curveName = resolveCurveName(ec);
          writeString(dos, "ecdsa-sha2-" + curveName);
          writeString(dos, curveName);
          final var point = ec.getW();
          final var x = toUnsignedBytes(point.getAffineX().toByteArray());
          final var y = toUnsignedBytes(point.getAffineY().toByteArray());
          final var uncompressed = new byte[1 + x.length + y.length];
          uncompressed[0] = EC_POINT_UNCOMPRESSED_PREFIX;
          System.arraycopy(x, 0, uncompressed, 1, x.length);
          System.arraycopy(y, 0, uncompressed, 1 + x.length, y.length);
          writeBytes(dos, uncompressed);
        }
        default -> throw new IOException("Unsupported key type: " + key.getClass().getName());
      }
    }

    return baos.toByteArray();
  }

  private static @NonNull String resolveCurveName(final @NonNull ECPublicKey key) {

    final var bitLength = key.getParams().getCurve().getField().getFieldSize();

    return switch (bitLength) {
      case EC_FIELD_SIZE_NIST_P256 -> "nistp256";
      case EC_FIELD_SIZE_NIST_P384 -> "nistp384";
      case EC_FIELD_SIZE_NIST_P521 -> "nistp521";
      default -> throw new IllegalArgumentException("Unsupported EC curve: " + bitLength);
    };
  }

  private static byte @NonNull [] toUnsignedBytes(final byte @NonNull [] bytes) {

    if (bytes.length > 1 && bytes[0] == 0) {
      final var result = new byte[bytes.length - 1];
      System.arraycopy(bytes, 1, result, 0, result.length);
      return result;
    }

    return bytes;
  }

  private static void writeString(final @NonNull DataOutputStream dos, final @NonNull String s)
      throws IOException {

    final var bytes = s.getBytes();

    dos.writeInt(bytes.length);
    dos.write(bytes);
  }

  private static void writeBytes(final @NonNull DataOutputStream dos, final byte @NonNull [] bytes)
      throws IOException {

    dos.writeInt(bytes.length);
    dos.write(bytes);
  }

  private @NonNull GitPackCommandFactory buildCommandFactory() {

    final GitLocationResolver resolver =
        (_, args, session, _) -> {
          final var tenant = this.resolveTenant(session);
          final var repoArg = this.parseRepoArg(args);
          final var owner = repoArg[0];
          final var repoName = repoArg[1];

          final var repoOpt = this.repoRepository.findByOwnerUsernameAndName(owner, repoName);

          if (repoOpt.isEmpty()) {
            throw new IOException("Repository not found: " + owner + "/" + repoName);
          }

          final var isWrite = args[0].contains("receive-pack");
          this.assertAccess(tenant, owner, repoName, isWrite);

          return Path.of(this.repoRoot);
        };

    return new GitPackCommandFactory(resolver);
  }

  private @NonNull Tenant resolveTenant(final @NonNull ServerSession session) throws IOException {

    final var tenant = session.getAttribute(TENANT_KEY);

    if (tenant == null) {
      throw new IOException("Not authenticated");
    }

    return tenant;
  }

  private @NonNull String[] parseRepoArg(final @NonNull String[] args) throws IOException {

    if (args.length < 2) {
      throw new IOException("Invalid git command args");
    }

    final var raw = args[1].replace(".git", "").replace("'", "").replace("\"", "").trim();
    final var normalized = raw.startsWith("/") ? raw.substring(1) : raw;
    final var parts = normalized.split("/");

    if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw new IOException("Invalid repository path: " + args[1]);
    }

    return new String[] {parts[0], parts[1]};
  }

  private @NonNull SimpleGeneratorHostKeyProvider createKeyProvider() {

    final var keyProvider = new SimpleGeneratorHostKeyProvider(Path.of(this.hostKeyPath));
    keyProvider.setAlgorithm("RSA");

    return keyProvider;
  }

  private void assertAccess(
      final @NonNull Tenant tenant,
      final @NonNull String owner,
      final @NonNull String repoName,
      final boolean isWrite) {

    log.debug(
        "Access check: tenant={}, repo={}/{}, write={}",
        tenant.getUsername(),
        owner,
        repoName,
        isWrite);
  }
}
