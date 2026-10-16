package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;

/**
 * A field was opened (or just saved) in the main panel of the tables &amp; fields screen: whatever shows
 * more of it — the conditional rules editor — loads itself for that field.
 */
public record FieldOpenedEvent(TypeFieldFormConfig field) {
}
