package fr.siamois.ui.api.openapi.v1;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * The response headers of an endpoint that still works but is on its way out (RFC 9745 {@code Deprecation},
 * and the replacement as a {@code successor-version} link). No {@code Sunset} (RFC 8594) is sent yet: the
 * removal date is to be agreed with the clients still calling these endpoints.
 */
public final class DeprecatedEndpointHeaders {

    public static final String DEPRECATION = "Deprecation";

    private DeprecatedEndpointHeaders() {
    }

    /**
     * @param builder   the response being built
     * @param successor the path of the endpoint to use instead, as a URI template
     * @return the same builder, with the headers added
     */
    public static ResponseEntity.BodyBuilder apply(ResponseEntity.BodyBuilder builder, String successor) {
        return builder
                .header(DEPRECATION, "true")
                .header(HttpHeaders.LINK, "<" + successor + ">; rel=\"successor-version\"");
    }
}
