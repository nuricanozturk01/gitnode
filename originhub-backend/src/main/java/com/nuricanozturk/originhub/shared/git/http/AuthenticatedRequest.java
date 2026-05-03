package com.nuricanozturk.originhub.shared.git.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.security.Principal;

class AuthenticatedRequest extends HttpServletRequestWrapper {

  private final String username;

  AuthenticatedRequest(final HttpServletRequest request, final String username) {
    super(request);
    this.username = username;
  }

  @Override
  public String getRemoteUser() {
    return this.username;
  }

  @Override
  public Principal getUserPrincipal() {
    return () -> this.username;
  }

  @Override
  public String getAuthType() {
    return "BASIC";
  }
}
