package dev.gitnode.os.shared.registries.repsy.services.service;

import dev.gitnode.os.events.auth.TenantRegisteredEvent;
import dev.gitnode.os.shared.errorhandling.exceptions.RepsyErrorOccurredException;
import dev.gitnode.os.shared.registries.repsy.dtos.*;
import dev.gitnode.os.shared.registries.repsy.services.remote.RepsyAuthServiceClient;
import dev.gitnode.os.shared.registries.repsy.services.remote.RepsyUserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepsyAuthService {

  private static final String TOKEN_BEARER = "Bearer ";
  private static final String ERR_REPSY_LOGIN_ERROR = "Repsy login failed";
  private static final String ERR_REPSY_USER_CREATE_ERROR = "Repsy user creation failed";

  private final RepsyAuthServiceClient authServiceClient;
  private final RepsyUserServiceClient userServiceClient;

  @Value("${gitnode.registries.repsy.admin.username}")
  private String adminUsername;

  @Value("${gitnode.registries.repsy.admin.password}")
  private String adminPassword;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void createRepsyUser(final TenantRegisteredEvent event) throws Exception{

    final var loginInfo = new RepsyLoginForm(this.adminUsername, this.adminPassword);
    final var adminLoginInfo = this.authServiceClient.login(loginInfo);

    this.checkRepsyResult(adminLoginInfo, ERR_REPSY_LOGIN_ERROR);

    final var token = TOKEN_BEARER + Objects.requireNonNull(adminLoginInfo.getData()).token();
    final var form = new RepsyUserCreateForm(event.username(), event.hash(), event.salt(), UserRole.USER.name());
    final var userResult = this.userServiceClient.create(token, form);

    this.checkRepsyResult(userResult, ERR_REPSY_USER_CREATE_ERROR);

    log.warn("Repsy user created in repsy: {}", event.username());
  }

  private void checkRepsyResult(final RepsyRestResponse<?> response, final String message) {
    if (response.getType() != ResponseType.SUCCESS || response.getData() == null) {
      log.error(message);
      throw new RepsyErrorOccurredException(message);
    }
  }
}
