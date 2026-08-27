package fr.siamois.ui.api.openapi.v1.request.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlacePatchRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void absentPlaceNumber_shouldRemainUnchanged() throws Exception {
        PlacePatchRequest request = objectMapper.readValue("{}", PlacePatchRequest.class);

        assertThat(request.isPlaceNumberPresent()).isFalse();
        assertThat(request.getPlaceNumber()).isNull();
    }

    @Test
    void explicitNullPlaceNumber_shouldRequestClearing() throws Exception {
        PlacePatchRequest request = objectMapper.readValue("{\"placeNumber\":null}", PlacePatchRequest.class);

        assertThat(request.isPlaceNumberPresent()).isTrue();
        assertThat(request.getPlaceNumber()).isNull();
    }

    @Test
    void numericalPlaceNumber_shouldRequestUpdate() throws Exception {
        PlacePatchRequest request = objectMapper.readValue("{\"placeNumber\":23}", PlacePatchRequest.class);

        assertThat(request.isPlaceNumberPresent()).isTrue();
        assertThat(request.getPlaceNumber()).isEqualTo(23);
    }
}
