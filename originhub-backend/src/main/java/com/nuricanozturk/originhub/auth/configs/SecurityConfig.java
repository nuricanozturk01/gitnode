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
package com.nuricanozturk.originhub.auth.configs;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@NullMarked
public class SecurityConfig {

  private static final long MAX_AGE = 3600L;
  private static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");
  private static final List<String> ALLOWED_HEADERS =
      List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin");
  private static final List<String> EXPOSED_HEADERS =
      List.of("Authorization", "Content-Disposition");

  private final CustomOauth2SuccessHandler successHandler;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final CustomAuthenticationEntryPoint authenticationEntryPoint;

  @Nullable
  @Autowired(required = false)
  private SamlAuthenticationSuccessHandler samlSuccessHandler;

  @Value("${originhub.cors.allowed-origins}")
  private List<String> allowedOrigins;

  @Bean
  public SecurityFilterChain doFilter(final HttpSecurity http) throws Exception {

    http.exceptionHandling(c -> c.authenticationEntryPoint(this.authenticationEntryPoint))
        .cors(this::cors)
        .headers(f -> f.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2Login(oauth2 -> oauth2.successHandler(this.successHandler))
        .authorizeHttpRequests(this::authorizeHttpRequests)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .addFilterBefore(this.jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    if (this.samlSuccessHandler != null) {
      http.saml2Login(saml2 -> saml2.successHandler(this.samlSuccessHandler));
    }

    return http.build();
  }

  private void authorizeHttpRequests(
      final AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          auth) {

    auth.requestMatchers(
            "/",
            "/index.html",
            "/*.js",
            "/*.css",
            "/*.ico",
            "/error",
            "/*.png",
            "/*.webmanifest",
            "/assets/**")
        .permitAll();

    auth.requestMatchers(
            "/api/auth/register",
            "/api/auth/refresh-token",
            "/api/auth/login",
            "/api/auth/sso/**",
            "/login/oauth2/code/*",
            "/oauth2/authorization/*",
            "/login/success",
            "/login/failure",
            "/logout/success")
        .permitAll();

    auth.requestMatchers("/saml2/**", "/login/saml2/**").permitAll();

    auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
    auth.requestMatchers("/actuator/**").authenticated();

    auth.requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll();
    auth.requestMatchers("/api/admin/**").authenticated();
    auth.requestMatchers("/public/**").permitAll();
    auth.requestMatchers("/git/**").permitAll();

    auth.requestMatchers(HttpMethod.GET, "/api/snippets/**").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/invitations/*").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/users/*").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/users/*/contributions").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/repo/*", "/api/repo/*/*").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/repos/**").permitAll();
    auth.requestMatchers(HttpMethod.GET, "/api/projects/**").permitAll();

    auth.requestMatchers("/api/**").authenticated();
    auth.anyRequest().permitAll();
  }

  private void cors(final CorsConfigurer<HttpSecurity> corsConfigurer) {

    corsConfigurer.configurationSource(this.corsConfigurationSource());
  }

  private CorsConfigurationSource corsConfigurationSource() {

    final var configuration = new CorsConfiguration();

    configuration.setAllowedOriginPatterns(this.allowedOrigins);
    configuration.setAllowCredentials(false);
    configuration.setAllowedMethods(ALLOWED_METHODS);
    configuration.setAllowedHeaders(ALLOWED_HEADERS);
    configuration.setExposedHeaders(EXPOSED_HEADERS);
    configuration.setMaxAge(MAX_AGE);

    final var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
  }
}
