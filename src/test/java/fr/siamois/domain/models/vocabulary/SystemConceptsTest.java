package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.exporttemplate.ExportSubject;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.services.exporttemplate.ExportFieldResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemConceptsTest {

    @Test
    void aliasedBindingsGetTheThesaurusConcept() {
        assertEquals("1503", SystemConcepts.id("SIAD", "title", "document.title"));
        assertEquals("1497", SystemConcepts.id("SIAD", "type", "document.type"));
        assertEquals("1516", SystemConcepts.id("SIAD", "finds", "document.finds"));
        assertEquals("1458", SystemConcepts.id("SIAST", "type", "study.type"));
    }

    @Test
    void fieldsAbsentFromTheThesaurusKeepTheirSymbolicId() {
        assertEquals("study.reportCode", SystemConcepts.id("SIAST", "reportCode", "study.reportCode"));
    }

    @Test
    void legacyIdsAreUpgradedPerTable() {
        assertEquals("1003", SystemConcepts.upgrade("SIAAU", "4285848"));
        assertEquals("1112", SystemConcepts.upgrade("SIASU", "4285848"));
        assertNull(SystemConcepts.upgrade("SIAAU", "unknown"));
    }

    @Test
    void documentSystemFieldsCarryTheSystemThesaurusConcepts() {
        var fields = new ExportFieldResolver(null, null).systemFields(ExportSubject.DOCUMENT);
        CustomField title = fields.stream().filter(f -> "title".equals(f.getValueBinding())).findFirst().orElseThrow();
        assertEquals("1503", title.getConcept().getExternalId());
        assertEquals("th332", title.getConcept().getVocabulary().getExternalVocabularyId());
        assertTrue(fields.stream().allMatch(f -> f.getConcept() == null
                || "th332".equals(f.getConcept().getVocabulary().getExternalVocabularyId())));
    }
}
