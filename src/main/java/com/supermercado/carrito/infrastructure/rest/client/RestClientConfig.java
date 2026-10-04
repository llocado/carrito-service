package com.supermercado.carrito.infrastructure.rest.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Con timeouts: si productos-service se cuelga, la peticion falla rapido
     * (y queda en el log) en vez de dejar hilos esperando indefinidamente.
     */
    @Bean
    public RestClient productosServiceRestClient(
            @Value("${productos-service.base-url}") String baseUrl,
            @Value("${productos-service.connect-timeout}") Duration connectTimeout,
            @Value("${productos-service.read-timeout}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
