package fr.siamois.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

@Data
public class FullInfoDTO implements Comparable<FullInfoDTO> {
    @JsonProperty("http://purl.org/dc/terms/contributor")
    private PurlInfoDTO[] contributor;

    @JsonProperty("http://purl.org/dc/terms/created")
    private PurlInfoDTO[] created;

    @JsonProperty("http://purl.org/dc/terms/identifier")
    private PurlInfoDTO[] identifier;

    @JsonProperty("http://purl.org/dc/terms/modified")
    private PurlInfoDTO[] modified;

    @JsonProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    private PurlInfoDTO[] type;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#broader")
    private PurlInfoDTO[] broader;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#inScheme")
    private PurlInfoDTO[] inScheme;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#narrower")
    private PurlInfoDTO[] narrower;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#prefLabel")
    private PurlInfoDTO[] prefLabel;

    @JsonProperty("http://purl.org/dc/terms/creator")
    private PurlInfoDTO[] creator;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#topConceptOf")
    private PurlInfoDTO[] topConceptOf;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#altLabel")
    private PurlInfoDTO[] altLabel;

    @JsonProperty("http://www.w3.org/2004/02/skos/core#notation")
    private PurlInfoDTO[] notation;

    @JsonProperty("http://purl.org/umu/uneskos#memberOf")
    private PurlInfoDTO[] memberOf;

    @JsonIgnore
    private String baseUri;

    public Optional<String> getFieldcode() {
        if (notation == null) return Optional.empty();
        return Arrays.stream(notation)
                .map(PurlInfoDTO::getValue)
                .filter(value -> value.startsWith("SIAMOIS#"))
                .map(value -> value.replace("SIAMOIS#", ""))
                .findFirst();
    }

    public String getIdentifierStr() {
        return identifier[0].getValue();
    }

    @Override
    public int compareTo(FullInfoDTO o) {
        return getIdentifierStr().compareTo(o.getIdentifierStr());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FullInfoDTO that)) return false;
        return Objects.deepEquals(identifier, that.identifier);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(identifier);
    }
}
