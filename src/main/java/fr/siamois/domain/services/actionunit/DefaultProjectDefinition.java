package fr.siamois.domain.services.actionunit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a "default project" is made of, written in {@code default-project/definition.json}: a thesaurus, and for each
 * table the types it starts with and, for each type, the collection of that thesaurus that lists the values of a field.
 * Concepts and collections are named by their identifiers in the thesaurus, so reading the definition needs no label
 * and no call to the thesaurus: it is imported once, when the application starts
 * ({@code DefaultProjectThesaurusInitializer}).
 */
@Component
public class DefaultProjectDefinition {

    static final String RESOURCE = "default-project/definition.json";
    private static final String VOCABULARY_MARKER = "/?idt=";

    /**
     * @param label            the type's name in the thesaurus, only to make the definition readable
     * @param concept          the identifier of the type's concept in the thesaurus
     * @param identifierFormat the format of the identifiers of this type's records, null for the table's default
     * @param lists            the collection (its identifier in the thesaurus) holding the values of a field for this type,
     *                         by field code; a field absent from here reads its whole list in the thesaurus
     * @param listNames        the name of each of those collections in the thesaurus, by field code: what finds the
     *                         same collection again when the field is moved to another thesaurus
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Type(String label, String concept, String identifierFormat, Map<String, String> lists,
                       Map<String, String> listNames) {
        public Type {
            lists = lists == null ? Map.of() : lists;
            listNames = listNames == null ? Map.of() : listNames;
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

    /** The URL of the thesaurus, e.g. {@code https://thesaurus.mom.fr/?idt=th337}. */
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

    /** Every collection the definition reads, so each is imported once. */
    public Set<String> collectionIds() {
        Set<String> ids = new LinkedHashSet<>();
        content.tables().forEach(table -> table.types().forEach(type -> ids.addAll(type.lists().values())));
        return ids;
    }
}
