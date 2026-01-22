package fr.siamois.domain.models.vocabulary;

import org.springframework.lang.NonNull;

public record ThesaurusInfo(
        String server,
        String idTheso,
        String label,
        String langLabel
) {
    @NonNull
    public String completeThesaurusUrl() {
        return server + "?idt=" + idTheso;
    }
}