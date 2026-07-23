package dev.gitnode.os.shared.registries.repsy;

import dev.gitnode.os.shared.registries.repsy.services.remote.RepsyAuthServiceClient;
import dev.gitnode.os.shared.registries.repsy.services.remote.RepsyUserServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ClientConfig {

    @Bean
    public RestClient sharedRestClient(@Value("${gitnode.registries.repsy.base-url}") String url) {
        return RestClient.builder()
                .baseUrl(url)
                .build();
    }

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient sharedRestClient) {
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(sharedRestClient))
                .build();
    }

    @Bean
    public RepsyUserServiceClient userClient(HttpServiceProxyFactory factory) {
        return factory.createClient(RepsyUserServiceClient.class);
    }

    @Bean
    public RepsyAuthServiceClient authClient(HttpServiceProxyFactory factory) {
        return factory.createClient(RepsyAuthServiceClient.class);
    }
}
