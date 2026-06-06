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
package com.nuricanozturk.originhub.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@NullMarked
public class RateLimitAspect {

  private static final String UNKNOWN = "unknown";
  private static final String KEY_PREFIX = "rate_limit:";
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";

  private final RateLimitService rateLimitService;

  @Around("@annotation(rateLimit)")
  public Object enforce(final ProceedingJoinPoint pjp, final RateLimit rateLimit) throws Throwable {

    final var subject = this.resolveSubject();
    final var methodKey =
        rateLimit.key().isBlank()
            ? pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName()
            : rateLimit.key();

    this.rateLimitService.enforce(
        KEY_PREFIX + methodKey + ":" + subject, rateLimit.limit(), rateLimit.windowSeconds());

    return pjp.proceed();
  }

  private String resolveSubject() {

    final var auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      return "user:" + auth.getName();
    }

    return "ip:" + this.resolveClientIp();
  }

  private String resolveClientIp() {

    @Nullable
    final ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

    if (attrs == null) {
      return UNKNOWN;
    }

    final HttpServletRequest request = attrs.getRequest();
    final var forwarded = request.getHeader(X_FORWARDED_FOR);

    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].strip();
    }

    return request.getRemoteAddr();
  }
}
