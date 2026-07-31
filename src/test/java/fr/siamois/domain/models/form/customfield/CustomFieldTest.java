package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomFieldTest {

    @Test
    void equals_trueForSameId_evenAcrossDifferentInstancesAndUnrelatedFields() {
        CustomFieldText a = CustomFieldText.builder().id(1L).label("A").build();
        CustomFieldText b = CustomFieldText.builder().id(1L).label("B").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_falseForDifferentIds() {
        CustomFieldText a = CustomFieldText.builder().id(1L).build();
        CustomFieldText b = CustomFieldText.builder().id(2L).build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_falseWhenIdIsNull_evenForOtherwiseIdenticalFields() {
        CustomFieldText a = CustomFieldText.builder().label("Same").build();
        CustomFieldText b = CustomFieldText.builder().label("Same").build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_trueForSameReference_evenWithNullId() {
        CustomFieldText a = CustomFieldText.builder().build();

        assertEquals(a, a);
    }

    @Test
    void equals_ignoresConcept_soTwoAdditionalFieldsWithoutAConceptAreNotConfusedWithEachOther() {
        // Additional fields created through the settings screen have no concept at all.
        CustomFieldText a = CustomFieldText.builder().id(1L).concept(null).build();
        CustomFieldInteger b = CustomFieldInteger.builder().id(2L).concept(null).build();

        assertNotEquals(a, b);
    }

    @Test
    void equals_doesNotCompareRuntimeClass_soAHibernateProxyOfTheSameRowStillMatches() {
        // Regression guard for the bug this fixes: FieldFormConfig#field is fetched LAZY, so a
        // Hibernate proxy (its own generated subclass, hence a different getClass()) must still
        // be considered equal to the concrete entity used elsewhere, as long as the id matches.
        // A different CustomField subclass stands in for "different getClass(), same row" here.
        CustomFieldText concrete = CustomFieldText.builder().id(1L).build();
        CustomFieldInteger differentRuntimeClassSameId = CustomFieldInteger.builder().id(1L).build();

        assertEquals(concrete, differentRuntimeClassSameId);
    }
}
