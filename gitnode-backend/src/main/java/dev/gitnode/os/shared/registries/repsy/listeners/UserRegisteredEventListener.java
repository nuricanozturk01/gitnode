package dev.gitnode.os.shared.registries.repsy.listeners;

import dev.gitnode.os.events.auth.TenantRegisteredEvent;
import dev.gitnode.os.shared.registries.repsy.services.service.RepsyAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegisteredEventListener {

  private final RepsyAuthService authService;

  @ApplicationModuleListener
  public void onUserRegistered(final TenantRegisteredEvent event) {
    try {
      this.authService.createRepsyUser(event);
    } catch (final Exception ex) {
      log.warn("Failed to create Repsy user for tenant {}", event.username(), ex);
    }
  }
}
