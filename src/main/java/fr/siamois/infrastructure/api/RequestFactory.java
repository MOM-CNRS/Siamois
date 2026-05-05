package fr.siamois.infrastructure.api;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Factory to build a RestTemplate with a custom request factory.
 */
@Service
public class RequestFactory {

    private final RestTemplateBuilder restTemplateBuilder;

    public RequestFactory(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    /**
     * Build a RestTemplate with the follow redirect option enabled.
     * @return The RestTemplate
     */
    public RestTemplate buildRestTemplate(boolean followRedirects) {
        return restTemplateBuilder.requestFactory(() -> new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(followRedirects);
            }
        }).build();
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder){
        return builder.build();
    }

}
