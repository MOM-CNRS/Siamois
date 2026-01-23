package fr.siamois.domain.models.vocabulary;

import org.springframework.lang.NonNull;

import java.io.Serializable;

public record ThesaurusInfo(
        String server,
        String idTheso,
        String label,
        String langLabel
) implements Serializable {
    @NonNull
    public String completeThesaurusUrl() {
        return server + "?idt=" + idTheso;
    }
}