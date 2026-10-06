package com.smartrecruit.config;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.ai.AiServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Client HTTP vers le service IA : URL de base, clé interne et délais d'attente. */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceConfig {

    /** Un service injoignable doit échouer vite, même si le délai de réponse autorisé est long. */
    static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient aiRestClient(RestClient.Builder builder, AiServiceProperties properties) {
        return buildRestClient(builder, properties);
    }

    public static RestClient buildRestClient(RestClient.Builder builder, AiServiceProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout.compareTo(MAX_CONNECT_TIMEOUT) < 0 ? timeout : MAX_CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        RestClient.Builder configured = builder.clone()
                .baseUrl(properties.url())
                .requestFactory(requestFactory);
        if (properties.isConfigured()) {
            configured.defaultHeader(AiServiceClient.API_KEY_HEADER, properties.apiKey());
        }
        return configured.build();
    }
}
