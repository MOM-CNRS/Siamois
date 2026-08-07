package fr.siamois.ui.api.openapi.v1.response.place;

import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlaceCreatedResponse extends Response<PlaceCreatedResponse.PlaceCreatedItem> {

    public PlaceCreatedResponse(PlaceCreatedItem data) {
        super(data);
    }

    @Data
    @Schema(description = "Lieu créé")
    public static class PlaceCreatedItem {

        @Schema(description = "Identifiant spatial_unit_id")
        private Long id;

        @Schema(description = "Nom du lieu")
        private String name;

        @Schema(description = "Code métier du lieu, si présent")
        private String code;

        @Schema(description = "Numéro de regroupement du lieu")
        private Integer placeNumber;

        public PlaceCreatedItem(Long id, String name, String code) {
            this(id, name, code, null);
        }

        public PlaceCreatedItem(Long id, String name, String code, Integer placeNumber) {
            this.id = id;
            this.name = name;
            this.code = code;
            this.placeNumber = placeNumber;
        }
    }
}
