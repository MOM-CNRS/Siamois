package fr.siamois.infrastructure.api.dto;

import lombok.Data;

import java.util.Objects;

@Data
public class PurlInfoDTO {
    private String value;
    private String type;
    private String datatype;
    private String lang;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PurlInfoDTO that)) return false;
        return Objects.equals(value, that.value) && Objects.equals(lang, that.lang);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, lang);
    }
}
