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
package dev.gitnode.os.actions;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gitnode.os.actions.entities.RunnerStatus;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ActionsEnumNames unit tests")
class ActionsEnumNamesTest {

  private final Locale previousDefault = Locale.getDefault();

  @AfterEach
  void restoreDefaultLocale() {
    Locale.setDefault(previousDefault);
  }

  @Test
  @DisplayName("lowerCase uses ROOT locale even when JVM default is Turkish")
  void lowerCase_usesRootLocale_underTurkishDefault() {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    assertThat(ActionsEnumNames.lowerCase(RunnerStatus.ONLINE)).isEqualTo("online");
    assertThat("ONLINE".toLowerCase()).isEqualTo("onlıne");
  }
}
