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

import dev.gitnode.os.shared.errorhandling.exceptions.BadRequestException;
import java.io.IOException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.ObjectLoader;

/** Central upper bounds for reading git objects fully into memory. */
public final class GitLimits {

  private GitLimits() {}

  /**
   * Reads the whole object, refusing objects larger than {@code max} bytes without loading them.
   *
   * @throws BadRequestException when the object exceeds {@code max}
   */
  public static byte[] readBounded(final ObjectLoader loader, final int max) throws IOException {
    if (loader.getSize() > max) {
      throw new BadRequestException("fileTooLarge: limit is " + max + " bytes");
    }
    try {
      return loader.getCachedBytes(max);
    } catch (final LargeObjectException ex) {
      throw new BadRequestException("fileTooLarge: limit is " + max + " bytes");
    }
  }
}
