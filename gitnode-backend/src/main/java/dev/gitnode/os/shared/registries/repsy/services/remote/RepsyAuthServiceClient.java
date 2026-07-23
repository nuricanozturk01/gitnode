package dev.gitnode.os.shared.registries.repsy.services.remote;


import dev.gitnode.os.shared.registries.repsy.dtos.RepsyLoginForm;
import dev.gitnode.os.shared.registries.repsy.dtos.RepsyLoginInfo;
import dev.gitnode.os.shared.registries.repsy.dtos.RepsyRestResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface RepsyAuthServiceClient {

  @PostExchange("/auth/login")
  RepsyRestResponse<RepsyLoginInfo> login(@RequestBody RepsyLoginForm form) throws Exception;
}
