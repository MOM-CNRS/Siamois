package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ThesaurusSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.UnitDefinitionSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitDefinitionInitializerTest {

    @Mock
    private ThesaurusSeeder thesaurusSeeder;
    @Mock
    private UnitDefinitionSeeder unitDefinitionSeeder;

    @InjectMocks
    private UnitDefinitionInitializer initializer;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() throws Exception {
        vocabulary = new Vocabulary();
        when(thesaurusSeeder.seed(List.of(new ThesaurusSeeder.ThesaurusSpec("https://thesaurus.mom.fr", "th252"))))
                .thenReturn(Map.of("th252", vocabulary));
    }

    /**
     * Without this, a freshly initialized database has no unit at all: the seeder that used to
     * create the metre went away with the default forms dataset.
     */
    @Test
    @DisplayName("Startup gives the instance the metre, in the thesaurus the units belong to")
    void initialize_seedsTheMetre() throws Exception {
        initializer.initialize();

        assertThat(seededUnits()).anySatisfy(metre -> {
            assertThat(metre.getSymbol()).isEqualTo("m");
            assertThat(metre.getLabel()).isEqualTo("Mètres");
            assertThat(metre.getDimension()).isEqualTo(UnitDefinition.Dimension.LENGTH);
            assertThat(metre.getFactorToBase()).isEqualTo(1.0);
            assertThat(metre.isSystemBase()).isTrue();
            assertThat(metre.getConcept().getExternalId()).isEqualTo("4289327");
            assertThat(metre.getConcept().getVocabulary().getExternalVocabularyId()).isEqualTo("th252");
        });
    }

    /**
     * Nothing else may create a unit, so a measurement expressed in one the catalogue omits fails
     * to save. Every unit the forms declare has to be in there — the centimetre and the kilogramme
     * have no concept in the thesaurus yet, and are identified by symbol and dimension alone.
     */
    @Test
    @DisplayName("Startup covers every unit the forms measure in")
    void initialize_seedsEveryUnitTheFormsUse() throws Exception {
        initializer.initialize();

        assertThat(seededUnits())
                .extracting(UnitDefinitionDTO::getSymbol, UnitDefinitionDTO::getDimension)
                .containsExactlyInAnyOrder(
                        tuple("m", UnitDefinition.Dimension.LENGTH),
                        tuple("cm", UnitDefinition.Dimension.LENGTH),
                        tuple("kg", UnitDefinition.Dimension.MASS));
    }

    /** Nothing to add to a unit already known: the seeder is what skips it, and it needs its id. */
    @Test
    @DisplayName("The units are seeded without an id, so fresh rows are inserted rather than merged")
    void initialize_seedsUnitsWithoutId() throws Exception {
        initializer.initialize();

        assertThat(seededUnits()).allSatisfy(unit -> assertThat(unit.getId()).isNull());
    }

    private List<UnitDefinitionDTO> seededUnits() {
        ArgumentCaptor<List<UnitDefinitionDTO>> seeded = ArgumentCaptor.captor();
        verify(unitDefinitionSeeder).seed(eq(vocabulary), seeded.capture());
        return seeded.getValue();
    }

}
