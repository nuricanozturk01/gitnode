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
package com.nuricanozturk.originhub.shared.audit.aspects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuricanozturk.originhub.shared.audit.annotations.Audited;
import com.nuricanozturk.originhub.shared.audit.services.AuditLogService;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditAspect unit tests")
class AuditAspectTest {

  @Mock private AuditLogService auditLogService;

  @InjectMocks private AuditAspect auditAspect;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Nested
  @DisplayName("actor resolution")
  class ActorResolution {

    @Test
    @DisplayName("passes null actor when no authentication set")
    void actor_isNull_whenNoAuth() {
      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(isNull(), eq("ACTION"), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("passes actor username when authenticated")
    void actor_isUsername_whenAuthenticated() {
      SecurityContextHolder.getContext()
          .setAuthentication(new UsernamePasswordAuthenticationToken("alice", null, List.of()));

      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(eq("alice"), eq("ACTION"), isNull(), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("passes null actor for anonymous principal")
    void actor_isNull_forAnonymous() {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(isNull(), eq("ACTION"), isNull(), isNull(), isNull(), any());
    }
  }

  @Nested
  @DisplayName("IP address resolution")
  class IpResolution {

    @Test
    @DisplayName("uses RemoteAddr when no X-Forwarded-For header")
    void ip_usesRemoteAddr_whenNoForwardedHeader() {
      var request = new MockHttpServletRequest();
      request.setRemoteAddr("10.0.0.1");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(any(), eq("ACTION"), any(), any(), any(), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("uses first X-Forwarded-For entry when header present")
    void ip_usesForwardedHeader_whenPresent() {
      var request = new MockHttpServletRequest();
      request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(any(), eq("ACTION"), any(), any(), any(), eq("1.2.3.4"));
    }

    @Test
    @DisplayName("passes null IP when not in servlet request context")
    void ip_isNull_whenNoRequestContext() {
      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(any(), eq("ACTION"), any(), any(), any(), isNull());
    }
  }

  @Nested
  @DisplayName("entityId SpEL resolution")
  class SpelResolution {

    @Test
    @DisplayName("entityId is null when SpEL expression is blank")
    void entityId_isNull_whenSpelBlank() {
      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "REPO", ""), "anything");

      verify(auditLogService).log(any(), eq("ACTION"), eq("REPO"), isNull(), any(), any());
    }

    @Test
    @DisplayName("entityId resolved from #result when return value used in SpEL")
    void entityId_fromResult_whenSpelUsesResult() {
      UUID id = UUID.randomUUID();

      auditAspect.afterAuditedMethod(
          jpWithParams(new String[0], new Object[0]),
          audited("CREATE_REPO", "REPO", "#result.toString()"),
          id);

      verify(auditLogService)
          .log(any(), eq("CREATE_REPO"), eq("REPO"), eq(id.toString()), any(), any());
    }

    @Test
    @DisplayName("entityId resolved from named method parameter")
    void entityId_fromParam_whenSpelUsesParamName() {
      UUID repoId = UUID.randomUUID();

      auditAspect.afterAuditedMethod(
          jpWithParams(new String[] {"repoId"}, new Object[] {repoId}),
          audited("DELETE_REPO", "REPO", "#repoId.toString()"),
          null);

      verify(auditLogService)
          .log(any(), eq("DELETE_REPO"), eq("REPO"), eq(repoId.toString()), any(), any());
    }

    @Test
    @DisplayName("entityId is null when SpEL expression cannot be evaluated")
    void entityId_isNull_whenSpelEvaluationFails() {
      auditAspect.afterAuditedMethod(
          jpWithParams(new String[0], new Object[0]),
          audited("ACTION", "REPO", "#nonexistent.missing()"),
          null);

      verify(auditLogService).log(any(), eq("ACTION"), eq("REPO"), isNull(), any(), any());
    }

    @Test
    @DisplayName("entityId uses record accessor method via SpEL")
    void entityId_fromRecordAccessor_whenSpelUsesIdMethod() {
      UUID id = UUID.randomUUID();
      record TestDto(UUID id) {}

      auditAspect.afterAuditedMethod(
          jpWithParams(new String[0], new Object[0]),
          audited("CREATE_THING", "THING", "#result.id().toString()"),
          new TestDto(id));

      verify(auditLogService)
          .log(any(), eq("CREATE_THING"), eq("THING"), eq(id.toString()), any(), any());
    }

    @Test
    @DisplayName("entityId is null when JoinPoint signature throws inside SpEL resolution")
    void entityId_isNull_whenSignatureThrows() {
      JoinPoint jp = mock(JoinPoint.class);
      MethodSignature sig = mock(MethodSignature.class);
      when(jp.getSignature()).thenReturn(sig);
      when(sig.getParameterNames()).thenThrow(new IllegalStateException("proxy error"));

      auditAspect.afterAuditedMethod(jp, audited("ACTION", "REPO", "#id.toString()"), null);

      verify(auditLogService).log(any(), eq("ACTION"), eq("REPO"), isNull(), any(), any());
    }
  }

  @Nested
  @DisplayName("entityType mapping")
  class EntityTypeMapping {

    @Test
    @DisplayName("entityType is passed through when non-blank")
    void entityType_passedThrough_whenPresent() {
      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "WEBHOOK", ""), null);

      verify(auditLogService).log(any(), any(), eq("WEBHOOK"), any(), any(), any());
    }

    @Test
    @DisplayName("entityType is null when annotation value is blank")
    void entityType_isNull_whenAnnotationBlank() {
      auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null);

      verify(auditLogService).log(any(), any(), isNull(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("exception safety")
  class ExceptionSafety {

    @Test
    @DisplayName("does not propagate exception when AuditLogService throws")
    void doesNotPropagate_whenServiceFails() {
      org.mockito.Mockito.doThrow(new RuntimeException("audit store unavailable"))
          .when(auditLogService)
          .log(any(), any(), any(), any(), any(), any());

      assertThatCode(
              () -> auditAspect.afterAuditedMethod(blankSpelJp(), audited("ACTION", "", ""), null))
          .doesNotThrowAnyException();
    }
  }

  private static JoinPoint blankSpelJp() {
    return mock(JoinPoint.class);
  }

  private static JoinPoint jpWithParams(String[] paramNames, Object[] args) {
    JoinPoint jp = mock(JoinPoint.class);
    MethodSignature sig = mock(MethodSignature.class);
    when(jp.getSignature()).thenReturn(sig);
    when(sig.getParameterNames()).thenReturn(paramNames);
    when(jp.getArgs()).thenReturn(args);
    return jp;
  }

  private static Audited audited(String action, String entityType, String spel) {
    Audited a = mock(Audited.class);
    when(a.action()).thenReturn(action);
    when(a.entityType()).thenReturn(entityType);
    when(a.entityIdSpEL()).thenReturn(spel);
    return a;
  }
}
