package fr.siamois.domain.models.document.form;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldFile;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.container.CustomFieldSelectMultipleContainer;
import fr.siamois.domain.models.form.customfield.person.CustomFieldSelectMultiplePerson;
import fr.siamois.domain.models.form.customfield.phase.CustomFieldSelectMultiplePhase;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnit;
import fr.siamois.domain.models.form.customfield.specimen.CustomFieldSelectMultipleSpecimen;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Transient;

import java.util.List;

import static fr.siamois.ui.form.FormLayoutConstants.SYSTEM_THESO;

/**
 * The document table's system fields: the national common base (identifiers, category, type, support,
 * description, authors, rights, keywords, technical data) and the document's links to the other
 * entities. Ids are stable negative numbers in the -701…-730 range.
 */
public abstract class DocumentForm {

    private static final String LABEL_PREFIX = "document.field.";
    private static final String B_ACTION_UNIT = "actionUnit";
    private static final String B_CATEGORY = "category";
    private static final String B_AUTHORS = "authors";
    private static final String B_CONTRIBUTORS = "contributors";
    private static final String B_PRODUCTION_DATE = "productionDate";
    private static final String B_ITEM_COUNT = "itemCount";
    private static final String B_SIZE_MB = "sizeMb";
    private static final String B_RECORDING_UNITS = "recordingUnits";
    private static final String B_FINDS = "finds";
    private static final String B_PLACES = "places";
    private static final String B_PHASES = "phases";
    private static final String B_CONTAINERS = "containers";

    protected DocumentForm() {}

    private static Concept concept(String externalId) {
        return new Concept.Builder().vocabulary(SYSTEM_THESO).externalId("document." + externalId).build();
    }

    private static CustomFieldText text(long id, String binding, boolean textArea) {
        return CustomFieldText.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .isTextArea(textArea)
                .id(id)
                .valueBinding(binding)
                .concept(concept(binding))
                .build();
    }

    private static CustomFieldSelectOneFromFieldCode one(long id, String binding, String fieldCode) {
        return CustomFieldSelectOneFromFieldCode.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .id(id)
                .valueBinding(binding)
                .fieldCode(fieldCode)
                .concept(concept(binding))
                .build();
    }

    private static CustomFieldSelectMultipleFromFieldCode many(long id, String binding, String fieldCode) {
        return CustomFieldSelectMultipleFromFieldCode.builder()
                .label(LABEL_PREFIX + binding)
                .isSystemField(true)
                .id(id)
                .valueBinding(binding)
                .fieldCode(fieldCode)
                .concept(concept(binding))
                .build();
    }

    @Transient
    protected static final CustomFieldText identifierField = text(-701L, "identifier", false);

    @Transient
    protected static final CustomFieldText otherIdentifiersField = text(-702L, "otherIdentifiers", false);

    @Transient
    protected static final CustomFieldSelectOneActionUnit actionUnitField = CustomFieldSelectOneActionUnit.builder()
            .label(LABEL_PREFIX + B_ACTION_UNIT)
            .isSystemField(true)
            .id(-703L)
            .valueBinding(B_ACTION_UNIT)
            .concept(concept(B_ACTION_UNIT))
            .build();

    /** The category: the table's type, hence the field that selects the form configuration. */
    @Transient
    protected static final CustomFieldSelectOneFromFieldCode categoryField = CustomFieldSelectOneFromFieldCode.builder()
            .label(LABEL_PREFIX + B_CATEGORY)
            .isSystemField(true)
            .id(-704L)
            .valueBinding(B_CATEGORY)
            .fieldCode(Document.TYPE_FIELD)
            .styleClass("mr-2 document-category-chip")
            .concept(concept(B_CATEGORY))
            .build();

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode documentTypeField =
            one(-705L, "documentType", Document.DOCUMENT_TYPE_FIELD);

    @Transient
    protected static final CustomFieldSelectMultipleFromFieldCode supportNaturesField =
            many(-706L, "supportNatures", Document.NATURE_FIELD_CODE);

    @Transient
    protected static final CustomFieldText formatField = text(-707L, "format", false);

    @Transient
    protected static final CustomFieldText titleField = text(-708L, "title", false);

    @Transient
    protected static final CustomFieldSelectMultiplePerson authorsField = CustomFieldSelectMultiplePerson.builder()
            .label(LABEL_PREFIX + B_AUTHORS)
            .isSystemField(true)
            .id(-709L)
            .valueBinding(B_AUTHORS)
            .concept(concept(B_AUTHORS))
            .build();

    @Transient
    protected static final CustomFieldSelectMultiplePerson contributorsField = CustomFieldSelectMultiplePerson.builder()
            .label(LABEL_PREFIX + B_CONTRIBUTORS)
            .isSystemField(true)
            .id(-710L)
            .valueBinding(B_CONTRIBUTORS)
            .concept(concept(B_CONTRIBUTORS))
            .build();

    @Transient
    protected static final CustomFieldText publisherField = text(-711L, "publisher", false);

    @Transient
    protected static final CustomFieldDateTime productionDateField = CustomFieldDateTime.builder()
            .label(LABEL_PREFIX + B_PRODUCTION_DATE)
            .isSystemField(true)
            .id(-712L)
            .valueBinding(B_PRODUCTION_DATE)
            .showTime(false)
            .concept(concept(B_PRODUCTION_DATE))
            .build();

    @Transient
    protected static final CustomFieldText descriptionField = text(-713L, "description", true);

    @Transient
    protected static final CustomFieldSelectOneFromFieldCode languageField =
            one(-714L, "language", Document.LANGUAGE_FIELD_CODE);

    @Transient
    protected static final CustomFieldText rightsField = text(-715L, "rights", false);

    @Transient
    protected static final CustomFieldSelectMultipleFromFieldCode keywordsField =
            many(-716L, "keywords", Document.KEYWORD_FIELD_CODE);

    @Transient
    protected static final CustomFieldInteger itemCountField = CustomFieldInteger.builder()
            .label(LABEL_PREFIX + B_ITEM_COUNT)
            .isSystemField(true)
            .id(-717L)
            .minValue(0)
            .maxValue(Integer.MAX_VALUE)
            .valueBinding(B_ITEM_COUNT)
            .concept(concept(B_ITEM_COUNT))
            .build();

    @Transient
    protected static final CustomFieldDecimal sizeMbField = CustomFieldDecimal.builder()
            .label(LABEL_PREFIX + B_SIZE_MB)
            .isSystemField(true)
            .id(-718L)
            .minValue(0.0)
            .maxValue(Double.MAX_VALUE)
            .valueBinding(B_SIZE_MB)
            .concept(concept(B_SIZE_MB))
            .build();

    @Transient
    protected static final CustomFieldText crsField = text(-719L, "crs", false);

    @Transient
    protected static final CustomFieldText originalPathField = text(-720L, "originalPath", false);

    @Transient
    protected static final CustomFieldText commentsField = text(-721L, "comments", true);

    /** The stored file: shown and replaced through the upload endpoints. */
    @Transient
    protected static final CustomFieldFile fileField = CustomFieldFile.builder()
            .label(LABEL_PREFIX + "file")
            .isSystemField(true)
            .id(-728L)
            .valueBinding("file")
            .concept(concept("file"))
            .build();

    /** The external URL, to which a document can point instead of (or besides) a stored file. */
    @Transient
    protected static final CustomFieldText externalUrlField = text(-722L, "externalUrl", false);

    // The links to the other entities: optional and multiple, written from the document's side.

    @Transient
    protected static final CustomFieldSelectMultipleRecordingUnit recordingUnitsField = CustomFieldSelectMultipleRecordingUnit.builder()
            .label(LABEL_PREFIX + B_RECORDING_UNITS)
            .isSystemField(true)
            .id(-723L)
            .valueBinding(B_RECORDING_UNITS)
            .concept(concept(B_RECORDING_UNITS))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleSpecimen findsField = CustomFieldSelectMultipleSpecimen.builder()
            .label(LABEL_PREFIX + B_FINDS)
            .isSystemField(true)
            .id(-724L)
            .valueBinding(B_FINDS)
            .concept(concept(B_FINDS))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleSpatialUnit placesField = CustomFieldSelectMultipleSpatialUnit.builder()
            .label(LABEL_PREFIX + B_PLACES)
            .isSystemField(true)
            .id(-725L)
            .valueBinding(B_PLACES)
            .concept(concept(B_PLACES))
            .build();

    @Transient
    protected static final CustomFieldSelectMultiplePhase phasesField = CustomFieldSelectMultiplePhase.builder()
            .label(LABEL_PREFIX + B_PHASES)
            .isSystemField(true)
            .id(-726L)
            .valueBinding(B_PHASES)
            .concept(concept(B_PHASES))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleContainer containersField = CustomFieldSelectMultipleContainer.builder()
            .label(LABEL_PREFIX + B_CONTAINERS)
            .isSystemField(true)
            .id(-727L)
            .valueBinding(B_CONTAINERS)
            .concept(concept(B_CONTAINERS))
            .build();

    /**
     * The table's system fields, in their default order, with the properties intrinsic to each.
     * This is the field set everything reads; layouts (groups, order, widths) live in configuration.
     */
    public static List<SystemFieldSpec> systemFields() {
        return List.of(
                // The identifier is shown in the header; the project is fixed once the document exists.
                SystemFieldSpec.hiddenReadOnly(identifierField),
                SystemFieldSpec.of(otherIdentifiersField),
                SystemFieldSpec.hiddenReadOnly(actionUnitField),
                SystemFieldSpec.hidden(categoryField),
                SystemFieldSpec.of(documentTypeField),
                SystemFieldSpec.of(supportNaturesField),
                SystemFieldSpec.of(formatField),
                SystemFieldSpec.of(titleField),
                SystemFieldSpec.of(authorsField),
                SystemFieldSpec.of(contributorsField),
                SystemFieldSpec.of(publisherField),
                SystemFieldSpec.of(productionDateField),
                SystemFieldSpec.of(descriptionField),
                SystemFieldSpec.of(languageField),
                SystemFieldSpec.of(rightsField),
                SystemFieldSpec.of(keywordsField),
                SystemFieldSpec.of(itemCountField),
                SystemFieldSpec.of(sizeMbField),
                SystemFieldSpec.of(crsField),
                SystemFieldSpec.of(originalPathField),
                SystemFieldSpec.of(commentsField),
                SystemFieldSpec.of(fileField),
                SystemFieldSpec.of(externalUrlField),
                SystemFieldSpec.of(recordingUnitsField),
                SystemFieldSpec.of(findsField),
                SystemFieldSpec.of(placesField),
                SystemFieldSpec.of(phasesField),
                SystemFieldSpec.of(containersField));
    }
}
