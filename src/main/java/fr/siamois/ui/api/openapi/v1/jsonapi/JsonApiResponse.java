package fr.siamois.ui.api.openapi.v1.jsonapi;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JsonApiResponse<T> {

    private T data;

    // Static generic helper to wrap any resource
    public static <T> JsonApiResponse<T> wrap(T resource) {
        return new JsonApiResponse<>(resource);
    }
}