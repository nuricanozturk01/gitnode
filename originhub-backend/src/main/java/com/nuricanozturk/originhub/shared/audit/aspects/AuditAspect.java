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

import com.nuricanozturk.originhub.shared.audit.annotations.Audited;
import com.nuricanozturk.originhub.shared.audit.services.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@NullMarked
@RequiredArgsConstructor
public class AuditAspect {

  private final AuditLogService auditLogService;

  @AfterReturning(value = "@annotation(audited)", returning = "returnValue")
  public void afterAuditedMethod(
      final JoinPoint jp, final Audited audited, final @Nullable Object returnValue) {
    try {
      final var actor = this.resolveActor();
      final var ip = this.resolveIpAddress();
      final var entityId = this.resolveEntityId(jp, audited, returnValue);
      this.auditLogService.log(
          actor, audited.action(), this.nullIfEmpty(audited.entityType()), entityId, null, ip);
    } catch (final Exception ex) {
      final var signature = jp.getSignature();
      log.debug(
          "AuditAspect failed for {}: {}",
          signature != null ? signature.toShortString() : "unknown",
          ex.getMessage());
    }
  }

  private @Nullable String resolveEntityId(
      final JoinPoint jp, final Audited audited, final @Nullable Object returnValue) {
    final var spel = audited.entityIdSpEL();
    if (spel.isBlank()) {
      return null;
    }
    try {
      final var parser = new SpelExpressionParser();
      final var context = new StandardEvaluationContext();
      context.setVariable("result", returnValue);
      final var sig = (MethodSignature) jp.getSignature();
      final var paramNames = sig.getParameterNames();
      final var args = jp.getArgs();
      if (paramNames != null) {
        for (int i = 0; i < paramNames.length; i++) {
          context.setVariable(paramNames[i], args[i]);
        }
      }
      final var value = parser.parseExpression(spel).getValue(context);
      return value != null ? value.toString() : null;
    } catch (final Exception ex) {
      log.debug("entityIdSpEL eval failed '{}': {}", spel, ex.getMessage());
      return null;
    }
  }

  private @Nullable String resolveActor() {
    final var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    return auth.getName();
  }

  private @Nullable String resolveIpAddress() {
    final var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
      return null;
    }
    final HttpServletRequest request = servletAttrs.getRequest();
    final var forwarded = request.getHeader("X-Forwarded-For");
    return (forwarded != null && !forwarded.isBlank())
        ? forwarded.split(",")[0].trim()
        : request.getRemoteAddr();
  }

  private @Nullable String nullIfEmpty(final @Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
