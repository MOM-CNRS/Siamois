package fr.siamois.domain.services.actionunit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * What a "default project" is made of, written in {@code default-project/definition.json}: a thesaurus, and for each
 * table the types it starts with. A type is named by its identifier in the thesaurus, so reading the definition needs no
 * label and no call to the thesaurus: it is imported once, when the application starts
 * ({@code DefaultProjectThesaurusInitializer}). The values a field offers for a type are not listed here: the thesaurus
 * relates them to the type, and the layouts' {@code RELATED_TO_TYPE} rule reads that relation.
 */
@Component
public class DefaultProjectDefinition {

    static final String RESOURCE = "default-project/definition.json";
    private static final String VOCABULARY_MARKER = "/?idt=";

    /**
     * @param label            the type's name in the thesaurus, only to make the definition readable
     * @param concept          the identifier of the type's concept in the thesaurus
     * @param identifierFormat the format of the identifiers of this type's records, null for the table's default
     * @param defaults         the concept (its identifier in the thesaurus) a new record of this type starts with, by field
     *                         code; only for a field whose list the thesaurus restricts to that one value for this type
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Type(String label, String concept, String identifierFormat, Map<String, String> defaults) {
        public Type {
            defaults = defaults == null ? Map.of() : defaults;
        }
    }

    /** @param typeCode the field code of the table's type: the thesaurus is asked for the types under it */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(ConfigurableTable table, String typeCode, List<Type> types) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Content(String thesaurus, List<Table> tables) {
    }

    private final Content content;

    public DefaultProjectDefinition() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            this.content = new ObjectMapper().readValue(in, Content.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /** The URL of the thesaurus, e.g. {@code https://thesaurus.mom.fr/?idt=th338}. */
    public String thesaurusUrl() {
        return content.thesaurus();
    }

    public String baseUri() {
        return content.thesaurus().substring(0, marker());
    }

    public String externalVocabularyId() {
        return content.thesaurus().substring(marker() + VOCABULARY_MARKER.length());
    }

    private int marker() {
        int marker = content.thesaurus().indexOf(VOCABULARY_MARKER);
        if (marker < 0) {
            throw new IllegalStateException("Not a thesaurus URL: " + content.thesaurus());
        }
        return marker;
    }

    public List<Table> tables() {
        return content.tables();
    }
}
