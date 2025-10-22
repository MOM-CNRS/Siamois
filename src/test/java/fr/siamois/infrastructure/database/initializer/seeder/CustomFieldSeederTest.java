package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldSeederTest {

    @Mock
    CustomFieldRepository customFieldRepository;
    @Mock
    ConceptSeeder conceptSeeder;

    @InjectMocks
    CustomFieldSeeder seeder;

    @Test
    void seed_AlreadyExists() throws DatabaseDataInitException {

        Concept c = new Concept();

        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey(
                "th230",
                "123456"
        );

        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(
                        CustomFieldText.class,
                        true,
                        "",
                        key,
                        "type",
                        "",
                        "",
                        ""
                )
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                CustomFieldText.class,
                true,
                "type",
                c
        )).thenReturn(Optional.of(new CustomFieldText()));
        seeder.seed(toInsert);

        verify(customFieldRepository, never()).save(any(CustomField.class));
    }


    @Test
    void seed_throw_ifConceptNotFound()  {


        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey(
                "th230",
                "123456"
        );

        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(
                        CustomFieldText.class,
                        true,
                        "",
                        key,
                        "type",
                        "",
                        "",
                        ""
                )
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenThrow(
                new IllegalStateException()
        );

        assertThrows(
                IllegalStateException.class,
                () -> seeder.seed(toInsert)
        );


    }

    @Test
    void seed_Create() throws DatabaseDataInitException {

        Concept c = new Concept();
        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey(
                "th230",
                "123456"
        );

        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(
                        CustomFieldSelectOneFromFieldCode.class,
                        true,
                        "",
                        key,
                        "type",
                        "",
                        "",
                        ""
                )
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                CustomFieldSelectOneFromFieldCode.class,
                true,
                "type",
                c
        )).thenReturn(Optional.empty());
        seeder.seed(toInsert);

        verify(customFieldRepository, times(1)).save(any(CustomField.class));
    }

    @Test
    void findFieldOrThrow_returnsField() {
        // Arrange
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("th230", "123456");

        CustomFieldSeederSpec spec = new CustomFieldSeederSpec(
                CustomFieldText.class,
                true,
                "",
                key,
                "type",
                "",
                "",
                ""
        );

        CustomFieldText expected = new CustomFieldText();

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                CustomFieldText.class,
                true,
                "type",
                c
        )).thenReturn(Optional.of(expected));

        // Act
        CustomField result = seeder.findFieldOrThrow(spec);

        // Assert
        assertSame(expected, result);
        verify(conceptSeeder).findConceptOrThrow(key);
        verify(customFieldRepository).findByTypeAndSystemAndBindingAndConcept(
                CustomFieldText.class, true, "type", c
        );
    }

    @Test
    void findFieldOrThrow_throwsWhenFieldMissing() {
        // Arrange
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("th230", "123456");

        CustomFieldSeederSpec spec = new CustomFieldSeederSpec(
                CustomFieldText.class,
                true,
                "",
                key,
                "type",
                "",
                "",
                ""
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(
                CustomFieldText.class,
                true,
                "type",
                c
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(IllegalStateException.class, () -> seeder.findFieldOrThrow(spec));

        verify(conceptSeeder).findConceptOrThrow(key);
        verify(customFieldRepository).findByTypeAndSystemAndBindingAndConcept(
                CustomFieldText.class, true, "type", c
        );
    }



}