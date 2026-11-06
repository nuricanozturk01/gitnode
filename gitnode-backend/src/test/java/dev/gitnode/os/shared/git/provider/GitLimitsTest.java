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
package dev.gitnode.os.shared.git.provider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectInserter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitLimitsTest {

  @TempDir Path dir;

  private org.eclipse.jgit.lib.ObjectLoader blob(final byte[] data) throws Exception {
    try (final var git = Git.init().setDirectory(this.dir.toFile()).call();
        final ObjectInserter ins = git.getRepository().newObjectInserter()) {
      final var id = ins.insert(Constants.OBJ_BLOB, data);
      ins.flush();
      return git.getRepository().open(id);
    }
  }

  @Test
  void readsObjectWithinLimit() throws Exception {
    final byte[] data = "hello".getBytes();
    assertArrayEquals(data, GitLimits.readBounded(this.blob(data), 10));
  }

  @Test
  void rejectsObjectOverLimit() throws Exception {
    final var loader = this.blob(new byte[100]);
    assertThrows(BadRequestException.class, () -> GitLimits.readBounded(loader, 10));
  }
}
