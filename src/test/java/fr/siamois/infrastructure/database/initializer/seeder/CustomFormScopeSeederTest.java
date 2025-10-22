package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.formscope.FormScope;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.initializer.seeder.customform.CustomFormScopeDTO;
import fr.siamois.infrastructure.database.initializer.seeder.customform.CustomFormScopeSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customform.CustomFormSeeder;
import fr.siamois.infrastructure.database.repositories.form.FormScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFormScopeSeederTest {

    @Mock private ConceptSeeder conceptSeeder;
    @Mock private CustomFormSeeder customFormSeeder;
    @Mock private FormScopeRepository formScopeRepository;

    @InjectMocks private CustomFormScopeSeeder seeder;

    // Using a mock for the DTO keeps the tests insulated from its concrete shape.
    @Mock private CustomFormScopeDTO dto;

    private Concept concept;
    private CustomForm form;

    @BeforeEach
    void setUp() {
        concept = new Concept();      // Adjust if your Concept needs construction helpers
        form = new CustomForm();      // Adjust if your CustomForm needs construction helpers
    }

    @Test
    void findOrReturnNull_returnsScopeWhenPresent() {
        FormScope existing = new FormScope();
        when(formScopeRepository.findGlobalByTypeAndForm(concept, form)).thenReturn(Optional.of(existing));

        FormScope result = seeder.findOrReturnNull(concept, form);

        assertThat(result).isSameAs(existing);
        verify(formScopeRepository).findGlobalByTypeAndForm(concept, form);
    }

    @Test
    void findOrReturnNull_returnsNullWhenAbsent() {
        when(formScopeRepository.findGlobalByTypeAndForm(concept, form)).thenReturn(Optional.empty());

        FormScope result = seeder.findOrReturnNull(concept, form);

        assertThat(result).isNull();
        verify(formScopeRepository).findGlobalByTypeAndForm(concept, form);
    }

    @Test
    void seed_createsScopeWhenMissing_withCorrectFields() throws Exception {
        // Arrange – the DTO just needs to hand inputs to the seeders.
        when(conceptSeeder.findConceptOrThrow(any())).thenReturn(concept);
        when(customFormSeeder.findOrThrow(any())).thenReturn(form);
        when(formScopeRepository.findGlobalByTypeAndForm(concept, form)).thenReturn(Optional.empty());

        // Act
        seeder.seed(List.of(dto));

        // Assert – verify a FormScope was saved with the expected values.
        ArgumentCaptor<FormScope> captor = ArgumentCaptor.forClass(FormScope.class);
        verify(formScopeRepository).save(captor.capture());
        FormScope saved = captor.getValue();

        assertThat(saved.getScopeLevel()).isEqualTo(FormScope.ScopeLevel.GLOBAL_DEFAULT);
        assertThat(saved.getType()).isSameAs(concept);
        assertThat(saved.getForm()).isSameAs(form);

        // And we should have looked up via repository first:
        verify(formScopeRepository).findGlobalByTypeAndForm(concept, form);
    }

    @Test
    void seed_doesNotCreateWhenAlreadyExists() throws Exception {
        when(conceptSeeder.findConceptOrThrow(any())).thenReturn(concept);
        when(customFormSeeder.findOrThrow(any())).thenReturn(form);
        when(formScopeRepository.findGlobalByTypeAndForm(concept, form)).thenReturn(Optional.of(new FormScope()));

        seeder.seed(List.of(dto));

        verify(formScopeRepository, never()).save(any(FormScope.class));
    }

    @Test
    void seed_propagatesConceptSeederException() {
        when(conceptSeeder.findConceptOrThrow(any()))
                .thenThrow(new IllegalStateException("Concept introuvable"));

        // Prepare the argument outside the lambda
        var dtos = List.of(dto);

        assertThatThrownBy(() -> seeder.seed(dtos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Concept introuvable");

        verify(formScopeRepository, never()).save(any());
    }


    @Test
    void seed_propagatesCustomFormSeederException()  {
        when(conceptSeeder.findConceptOrThrow(any())).thenReturn(concept);
        when(customFormSeeder.findOrThrow(any()))
                .thenThrow(new IllegalStateException("Form introuvable"));

        // Build args outside the lambda to keep a single call inside it
        var dtos = List.of(dto);

        assertThatThrownBy(() -> seeder.seed(dtos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Form introuvable");

        verify(formScopeRepository, never()).save(any());
    }
}

