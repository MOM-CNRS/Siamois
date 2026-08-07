package fr.siamois.domain.models.settings.tableconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One piece of a table identifier format: either literal text or a registered placeholder.
 * UI-only — {@code TypeFormConfig.identifierFormat} is the persisted string form.
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
