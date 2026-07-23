package dev.gitnode.os.shared.registries.repsy.services.remote;

import dev.gitnode.os.shared.registries.repsy.dtos.RepsyUserCreateForm;
import dev.gitnode.os.shared.registries.repsy.dtos.RepsyUserInfo;
import dev.gitnode.os.shared.registries.repsy.dtos.RepsyRestResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface RepsyUserServiceClient {

  @PostExchange("/users")
  RepsyRestResponse<RepsyUserInfo> create(@RequestHeader("Authorization") String authorization, @RequestBody RepsyUserCreateForm form) throws Exception;
}
