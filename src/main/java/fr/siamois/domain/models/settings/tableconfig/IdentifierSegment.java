package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One piece of the UE identifier format builder shown on the "Identifiants" tab: either a literal
 * text chunk or a placeholder bound to a {@code RuIdentifierResolver} code (e.g. {@code NUM_UE}).
 * UI-only — {@code ActionUnitDTO.recordingUnitIdentifierFormat} is the string form actually
 * persisted; segments are parsed from and serialized back to that string.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class IdentifierSegment implements Serializable {
    private boolean token;
    private String text;
    private String code;
    private String label;
    private boolean numeric;
    private int digits;
}
