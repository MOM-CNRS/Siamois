package fr.siamois.ui.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Ignore les champs null dans le JSON
public class RecordingUnitResponse {

    private Long id;                       // recording_unit_id
    private Integer identifier;            // identifier
    private String fullIdentifier;         // full_identifier
    private String description;            // description
    private OffsetDateTime openingDate;    // start_date
    private OffsetDateTime closingDate;    // end_date
    private String matrixComposition;      // matrix_composition
    private String matrixColor;            // matrix_color
    private String matrixTexture;          // matrix_texture
    private String erosionShape;           // erosion_shape
    private String erosionOrientation;     // erosion_orientation
    private String erosionProfile;         // erosion_profile
    private Integer taq;                   // taq
    private Integer tpq;                   // tpq
}