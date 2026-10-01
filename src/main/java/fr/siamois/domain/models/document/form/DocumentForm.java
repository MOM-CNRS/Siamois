package fr.siamois.domain.models.document.form;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
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
            .label(LABEL_PREFIX + "actionUnit")
            .isSystemField(true)
            .id(-703L)
            .valueBinding("actionUnit")
            .concept(concept("actionUnit"))
            .build();

    /** The category: the table's type, hence the field that selects the form configuration. */
    @Transient
    protected static final CustomFieldSelectOneFromFieldCode categoryField = CustomFieldSelectOneFromFieldCode.builder()
            .label(LABEL_PREFIX + "category")
            .isSystemField(true)
            .id(-704L)
            .valueBinding("category")
            .fieldCode(Document.TYPE_FIELD)
            .styleClass("mr-2 document-category-chip")
            .concept(concept("category"))
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
            .label(LABEL_PREFIX + "authors")
            .isSystemField(true)
            .id(-709L)
            .valueBinding("authors")
            .concept(concept("authors"))
            .build();

    @Transient
    protected static final CustomFieldSelectMultiplePerson contributorsField = CustomFieldSelectMultiplePerson.builder()
            .label(LABEL_PREFIX + "contributors")
            .isSystemField(true)
            .id(-710L)
            .valueBinding("contributors")
            .concept(concept("contributors"))
            .build();

    @Transient
    protected static final CustomFieldText publisherField = text(-711L, "publisher", false);

    @Transient
    protected static final CustomFieldDateTime productionDateField = CustomFieldDateTime.builder()
            .label(LABEL_PREFIX + "productionDate")
            .isSystemField(true)
            .id(-712L)
            .valueBinding("productionDate")
            .showTime(false)
            .concept(concept("productionDate"))
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
            .label(LABEL_PREFIX + "itemCount")
            .isSystemField(true)
            .id(-717L)
            .minValue(0)
            .maxValue(Integer.MAX_VALUE)
            .valueBinding("itemCount")
            .concept(concept("itemCount"))
            .build();

    @Transient
    protected static final CustomFieldDecimal sizeMbField = CustomFieldDecimal.builder()
            .label(LABEL_PREFIX + "sizeMb")
            .isSystemField(true)
            .id(-718L)
            .minValue(0.0)
            .maxValue(Double.MAX_VALUE)
            .valueBinding("sizeMb")
            .concept(concept("sizeMb"))
            .build();

    @Transient
    protected static final CustomFieldText crsField = text(-719L, "crs", false);

    @Transient
    protected static final CustomFieldText originalPathField = text(-720L, "originalPath", false);

    @Transient
    protected static final CustomFieldText commentsField = text(-721L, "comments", true);

    /** The external URL; the stored file's own field comes with the upload (it has its own answer type). */
    @Transient
    protected static final CustomFieldText externalUrlField = text(-722L, "externalUrl", false);

    // The links to the other entities: optional and multiple, written from the document's side.

    @Transient
    protected static final CustomFieldSelectMultipleRecordingUnit recordingUnitsField = CustomFieldSelectMultipleRecordingUnit.builder()
            .label(LABEL_PREFIX + "recordingUnits")
            .isSystemField(true)
            .id(-723L)
            .valueBinding("recordingUnits")
            .concept(concept("recordingUnits"))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleSpecimen findsField = CustomFieldSelectMultipleSpecimen.builder()
            .label(LABEL_PREFIX + "finds")
            .isSystemField(true)
            .id(-724L)
            .valueBinding("finds")
            .concept(concept("finds"))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleSpatialUnit placesField = CustomFieldSelectMultipleSpatialUnit.builder()
            .label(LABEL_PREFIX + "places")
            .isSystemField(true)
            .id(-725L)
            .valueBinding("places")
            .concept(concept("places"))
            .build();

    @Transient
    protected static final CustomFieldSelectMultiplePhase phasesField = CustomFieldSelectMultiplePhase.builder()
            .label(LABEL_PREFIX + "phases")
            .isSystemField(true)
            .id(-726L)
            .valueBinding("phases")
            .concept(concept("phases"))
            .build();

    @Transient
    protected static final CustomFieldSelectMultipleContainer containersField = CustomFieldSelectMultipleContainer.builder()
            .label(LABEL_PREFIX + "containers")
            .isSystemField(true)
            .id(-727L)
            .valueBinding("containers")
            .concept(concept("containers"))
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
                SystemFieldSpec.of(categoryField),
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
                SystemFieldSpec.of(externalUrlField),
                SystemFieldSpec.of(recordingUnitsField),
                SystemFieldSpec.of(findsField),
                SystemFieldSpec.of(placesField),
                SystemFieldSpec.of(phasesField),
                SystemFieldSpec.of(containersField));
    }
}
