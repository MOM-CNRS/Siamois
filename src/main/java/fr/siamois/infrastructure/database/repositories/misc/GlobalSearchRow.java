package fr.siamois.infrastructure.database.repositories.misc;

/** Une ligne de la fonction SQL {@code global_search}. */
public record GlobalSearchRow(String entityType, Long entityId, String label, String subtitle,
                              Long projectId, long total) {
}
