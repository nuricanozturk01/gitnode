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
package dev.gitnode.os.ai.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CodebaseAnalysisService parser tests")
class CodebaseAnalysisServiceTest {

  @Test
  @DisplayName("parseStructuredResponse extracts SECURITY fields")
  void parseStructuredResponse_extractsSecurityFields() throws Exception {
    final var raw =
        """
        ARCH_SCORE:8
        ARCH_REASON:Layered modules
        SECURITY_SCORE:6
        SECURITY_REASON:JWT secret in config
        SECURITY_ISSUES:Hardcoded secrets, missing rate limits
        SECURITY_FIX:Move secrets to env vars and add rate limiting
        SUMMARY:Solid architecture with security gaps
        """;

    final var fields = invokeParseStructuredResponse(raw);

    assertThat(fields.get("SECURITY_SCORE")).isEqualTo("6");
    assertThat(fields.get("SECURITY_REASON")).contains("JWT");
    assertThat(fields.get("SECURITY_ISSUES")).contains("Hardcoded secrets");
    assertThat(fields.get("SECURITY_FIX")).contains("env vars");
  }

  @Test
  @DisplayName("parseScoreValue accepts score formats with suffix")
  void parseScoreValue_acceptsScoreWithSuffix() throws Exception {
    assertThat(invokeParseScoreValue("8/10")).isEqualTo((short) 8);
    assertThat(invokeParseScoreValue("Score: 7")).isEqualTo((short) 7);
    assertThat(invokeParseScoreValue("10")).isEqualTo((short) 10);
    assertThat(invokeParseScoreValue("0")).isNull();
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> invokeParseStructuredResponse(final String raw) throws Exception {
    final Method method =
        CodebaseAnalysisService.class.getDeclaredMethod("parseStructuredResponse", String.class);
    method.setAccessible(true);
    final var service = new CodebaseAnalysisService(null, null, null, null);
    return (Map<String, String>) method.invoke(service, raw);
  }

  private Short invokeParseScoreValue(final String value) throws Exception {
    final Method method =
        CodebaseAnalysisService.class.getDeclaredMethod("parseScoreValue", String.class);
    method.setAccessible(true);
    final var service = new CodebaseAnalysisService(null, null, null, null);
    return (Short) method.invoke(service, value);
  }
}
