package fr.siamois.domain.models.settings.tableconfig;

import lombok.Getter;

@Getter
public enum FieldType {
    TEXTE("Texte"),
    NUMERIQUE("Numérique"),
    MESURE("Mesure"),
    VOCABULAIRE_CONTROLE("Vocabulaire contrôlé"),
    TYPOLOGIE("Typologie"),
    PARENTS("Parents"),
    ENFANTS("Enfants"),
    UNITE_ENREGISTREMENT("Unité d'enregistrement"),
    LIEU("Lieu"),
    PROJET("Projet");

    private final String label;

    FieldType(String label) {
        this.label = label;
    }

    public boolean isConfigurable() {
        return this == VOCABULAIRE_CONTROLE || this == TYPOLOGIE;
    }
}
