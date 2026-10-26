package fr.siamois.domain.models.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * One place where a project or an organisation draws a list from a thesaurus, for a field and an
 * entity type: through a field code, a branch or a collection.
 *
 * A class rather than a record: the EL of the JSF pages (EL 5) only reads JavaBean getters.
 *
 * @param thesaurusLabel label of the thesaurus the source belongs to
 * @param kind           field code, branch or collection
 * @param sourceLabel    the field code, the label of the branch's top term, or the external id of the collection
 * @param fieldLabel     the field the list is used in
 * @param typeLabel      the table and entity type the configuration is specific to, or null for the default one
 * @param tableName      the {@code ConfigurableTable} of the type, or null when not known
 * @param typeName       the name of the entity type, or null for the default configuration
 * @param fieldCode      the field code the field is connected to, for {@link FieldVocabularyConfig.Kind#FIELD_CODE}
 * @param inherited      whether nothing is stored for the field: it follows the default source of its code
 * @param thesaurusUrl   the URL of that thesaurus, or null for a field that follows the default source of its code
 * @param formConfigId   the form configuration the stored source belongs to, null when nothing is stored
 * @param fieldId        the field the stored source belongs to, null when nothing is stored
 * @param projectId      the project the configuration belongs to, null for one set at the organisation level
 * @param projectName    the name of that project
 */
@Value
@AllArgsConstructor
public class ThesaurusUsage implements Serializable {
    String thesaurusLabel;
    FieldVocabularyConfig.Kind kind;
    String sourceLabel;
    String fieldLabel;
    @Nullable String typeLabel;
    @Nullable String tableName;
    @Nullable String typeName;
    @Nullable String fieldCode;
    boolean inherited;
    @Nullable String thesaurusUrl;
    @Nullable Long formConfigId;
    @Nullable Long fieldId;
    @Nullable Long projectId;
    @Nullable String projectName;

    /** A source with nothing stored behind it, or one whose stored form is not known. */
    public ThesaurusUsage(String thesaurusLabel, FieldVocabularyConfig.Kind kind, String sourceLabel, String fieldLabel,
                          @Nullable String typeLabel, @Nullable String tableName, @Nullable String typeName,
                          @Nullable String fieldCode, boolean inherited) {
        this(thesaurusLabel, kind, sourceLabel, fieldLabel, typeLabel, tableName, typeName, fieldCode, inherited, null, null, null, null, null);
    }

    /** A stored source whose project is not known. */
    public ThesaurusUsage(String thesaurusLabel, FieldVocabularyConfig.Kind kind, String sourceLabel, String fieldLabel,
                          @Nullable String typeLabel, @Nullable String tableName, @Nullable String typeName,
                          @Nullable String fieldCode, boolean inherited,
                          @Nullable String thesaurusUrl, @Nullable Long formConfigId, @Nullable Long fieldId) {
        this(thesaurusLabel, kind, sourceLabel, fieldLabel, typeLabel, tableName, typeName, fieldCode, inherited,
                thesaurusUrl, formConfigId, fieldId, null, null);
    }

    /** What identifies the row among the others, for the selection of the table. */
    public String getRowKey() {
        if (formConfigId != null && fieldId != null) {
            return "stored|" + formConfigId + "|" + fieldId;
        }
        return tableName + "|" + typeName + "|" + fieldLabel;
    }
}
