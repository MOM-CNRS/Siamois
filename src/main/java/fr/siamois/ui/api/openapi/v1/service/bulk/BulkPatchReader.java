package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads a bulk PATCH body into the collection's own unit PATCH request type.
 */
@Component
@RequiredArgsConstructor
public class BulkPatchReader {

    private final ObjectMapper objectMapper;

    /**
     * Converts the bulk patch into the unit request type.
     *
     * @param patch the patch as sent
     * @param type  the collection's unit PATCH request class
     * @param <T>   the request type
     * @return the typed request
     * @throws ResponseStatusException 400 when the patch does not fit the type
     */
    public <T> T read(JsonNode patch, Class<T> type) {
        try {
            return objectMapper.treeToValue(patch, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mise à jour invalide : " + e.getMessage(), e);
        }
    }
}
