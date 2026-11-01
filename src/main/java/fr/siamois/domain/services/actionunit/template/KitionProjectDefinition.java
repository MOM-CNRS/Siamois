package fr.siamois.domain.services.actionunit.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * What the Kition-Pervolia project template is made of, written in {@code kition-project/definition.json}: the types the
 * project keeps (read from a branch of the Kition thesaurus), the fields whose list comes from another branch of it,
 * the common-core vocabulary fields that stay, and the fields (text, date, measurement) added to some types.
 * Types and branches are named by their identifier in the thesaurus; labels are only there to make the file readable
 * and to find a type again once it is configured.
 */
@Component
public class KitionProjectDefinition {

    static final String RESOURCE = "kition-project/definition.json";
    private static final String VOCABULARY_MARKER = "?idt=";

    /** @param label the type's name in the thesaurus; @param concept its identifier there; @param identifierFormat null for the table's default */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Type(String label, String concept, String identifierFormat) {
    }

    /** @param typeBranch the top of the thesaurus branch the table's types are read from */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(ConfigurableTable table, String typeBranch, List<Type> types) {
    }

    /** A vocabulary field of the table whose list is the subtree of {@code branch}; {@code types} empty means every type. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchField(ConfigurableTable table, String fieldCode, String branch, List<String> types) {
        public BranchField {
            types = types == null ? List.of() : types;
        }
    }

    /** A vocabulary field kept on the common-core thesaurus; {@code types} empty means every type. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommonCoreField(ConfigurableTable table, String fieldCode, List<String> types) {
        public CommonCoreField {
            types = types == null ? List.of() : types;
        }
    }

    /**
     * A field added to the named types of the table: free text by default, or a date, or a measurement entered in
     * {@code unit} (its symbol, e.g. {@code m}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtraField(ConfigurableTable table, List<String> types, String name, String description,
                             FieldType fieldType, String unit) {
        public ExtraField {
            types = types == null ? List.of() : types;
            fieldType = fieldType == null ? FieldType.TEXT : fieldType;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Content(String thesaurus, List<Table> tables, List<BranchField> branchFields,
                           List<CommonCoreField> commonCoreFields, List<ExtraField> extraFields) {
    }

    private final Content content;

    public KitionProjectDefinition() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            this.content = new ObjectMapper().readValue(in, Content.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    /** The URL of the thesaurus, e.g. {@code https://thesaurus.mom.fr/?idt=th182}. */
    public String thesaurusUrl() {
        return content.thesaurus();
    }

    /** The base URI of the thesaurus, with its trailing slash, e.g. {@code https://thesaurus.mom.fr/}. */
    public String baseUri() {
        return content.thesaurus().substring(0, marker());
    }

    /** The thesaurus' own identifier, e.g. {@code th182}. */
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

    public List<BranchField> branchFields() {
        return content.branchFields();
    }

    public List<CommonCoreField> commonCoreFields() {
        return content.commonCoreFields();
    }

    public List<ExtraField> extraFields() {
        return content.extraFields();
    }
}
