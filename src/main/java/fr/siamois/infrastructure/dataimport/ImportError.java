package fr.siamois.infrastructure.dataimport;

public record ImportError(String sheet, int row, String message) {}
