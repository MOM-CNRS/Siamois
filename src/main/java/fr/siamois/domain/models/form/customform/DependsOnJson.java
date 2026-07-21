package fr.siamois.domain.models.form.customform;

import lombok.Data;

import java.io.Serializable;

@Data
public class DependsOnJson implements Serializable {
    private Long fieldId;            // ID du CustomField dont la réponse (Concept) sert de base value
}
