package fr.siamois.domain.services.exporttemplate;

import org.springframework.lang.Nullable;

/**
 * Avertissement non bloquant d'un export : le fichier est produit, mais une colonne est restée vide ou
 * une valeur a été modifiée. {@code code} identifie le cas (l'interface le traduit), {@code sheet} et
 * {@code column} le situent, {@code detail} précise (nom du champ, valeur en cause…).
 */
public record ExportWarning(Code code, @Nullable String sheet, @Nullable String column, @Nullable String detail) {

    public enum Code {
        /** Le champ désigné par un concept n'existe pas dans ce projet : la colonne reste vide. */
        FIELD_UNRESOLVED,
        /** Une valeur de colonne numérique n'est pas un nombre : elle est écrite comme texte. */
        NOT_NUMERIC,
        /** Un type de valeur que l'export ne sait pas écrire : la cellule reste vide. */
        UNSUPPORTED_VALUE,
        /** Une cellule dépasse la limite d'Excel (32 767 caractères) : elle a été coupée. */
        CELL_TRUNCATED,
        /** Un nom de feuille a dû être modifié pour être accepté par Excel. */
        SHEET_RENAMED,
        /** Toutes les feuilles sont vides : seule la première est écrite, avec ses en-têtes. */
        EMPTY_WORKBOOK,
        /** Le patron du nom de fichier contient un champ inconnu. */
        UNKNOWN_PLACEHOLDER
    }
}
