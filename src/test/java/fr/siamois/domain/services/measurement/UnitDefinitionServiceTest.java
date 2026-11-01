package fr.siamois.domain.services.measurement;

import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnitDefinitionServiceTest {

    @Mock
    private UnitDefinitionRepository unitDefinitionRepository;
    @Mock
    private UnitDefinitionMapper unitDefinitionMapper;

    @InjectMocks
    private UnitDefinitionService service;

    /**
     * The case that filled the table with duplicate metres: the system fields build their unit in
     * code, with no id, and saving it as it stood inserted a copy of it every time.
     */
    @Test
    @DisplayName("A unit built in code resolves to the stored one of the same symbol and dimension")
    void resolve_findsTheStoredUnitOfAUnitWithNoId() {
        UnitDefinition stored = metre(5L);
        when(unitDefinitionRepository.findFirstBySymbolAndDimensionOrderByIdAsc("m", UnitDefinition.Dimension.LENGTH))
                .thenReturn(Optional.of(stored));

        assertThat(service.resolve(metre(null))).isSameAs(stored);
    }

    @Test
    @DisplayName("A unit that already has an id resolves to its row")
    void resolve_findsTheStoredUnitById() {
        UnitDefinition stored = metre(5L);
        when(unitDefinitionRepository.findById(5L)).thenReturn(Optional.of(stored));

        assertThat(service.resolve(metre(5L))).isSameAs(stored);
    }

    /** Failing beats creating: units are seeded, and a measurement must not invent one. */
    @Test
    @DisplayName("A unit the instance does not know fails rather than being created")
    void resolve_failsWhenNoUnitIsSeededForThatSymbol() {
        when(unitDefinitionRepository.findFirstBySymbolAndDimensionOrderByIdAsc("m", UnitDefinition.Dimension.LENGTH))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(metre(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("m");
    }

    @Test
    @DisplayName("A measurement without a unit asks for nothing")
    void resolve_returnsNullForNoUnit() {
        assertThat(service.resolve(null)).isNull();
        assertThat(service.resolveById(null)).isNull();
        verifyNoInteractions(unitDefinitionRepository);
    }

    @Test
    @DisplayName("An id pointing at a row that is gone fails")
    void resolveById_failsWhenTheRowIsGone() {
        when(unitDefinitionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveById(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("Every seeded unit is offered, alphabetically")
    void findOptions_returnsEveryUnit() {
        UnitDefinition stored = metre(5L);
        UnitDefinitionDTO dto = UnitDefinitionDTO.builder().id(5L).symbol("m").build();
        when(unitDefinitionRepository.findAllByOrderByLabelAsc()).thenReturn(List.of(stored));
        when(unitDefinitionMapper.convert(stored)).thenReturn(dto);

        assertThat(service.findOptions()).containsExactly(dto);
    }

    /**
     * The incoming measurement is rebuilt from a DTO and carries the stored one's id: set as is on a
     * managed entity, it is a detached instance the next flush fails to persist.
     */
    @Test
    @DisplayName("A measurement entered over a stored one updates the stored one")
    void mergeMeasurementAnswer_updatesTheManagedMeasurement() {
        UnitDefinition stored = metre(5L);
        when(unitDefinitionRepository.findById(5L)).thenReturn(Optional.of(stored));
        MeasurementAnswer managed = MeasurementAnswer.builder().id(11L).numericValue(2.0).unit(stored).build();
        MeasurementAnswer incoming = MeasurementAnswer.builder().id(11L).numericValue(4.5).comment("sec").unit(metre(5L)).build();

        MeasurementAnswer merged = service.mergeMeasurementAnswer(incoming, managed);

        assertThat(merged).isSameAs(managed);
        assertThat(merged.getNumericValue()).isEqualTo(4.5);
        assertThat(merged.getComment()).isEqualTo("sec");
        assertThat(merged.getUnit()).isSameAs(stored);
    }

    @Test
    @DisplayName("A first measurement is a new one, in the stored unit")
    void mergeMeasurementAnswer_createsOneWhenThereIsNone() {
        UnitDefinition stored = metre(5L);
        when(unitDefinitionRepository.findFirstBySymbolAndDimensionOrderByIdAsc("m", UnitDefinition.Dimension.LENGTH))
                .thenReturn(Optional.of(stored));

        MeasurementAnswer merged = service.mergeMeasurementAnswer(
                MeasurementAnswer.builder().numericValue(3.0).unit(metre(null)).build(), null);

        assertThat(merged.getId()).isNull();
        assertThat(merged.getNumericValue()).isEqualTo(3.0);
        assertThat(merged.getUnit()).isSameAs(stored);
    }

    @Test
    @DisplayName("A cleared measurement is none")
    void mergeMeasurementAnswer_returnsNullWhenCleared() {
        assertThat(service.mergeMeasurementAnswer(null, MeasurementAnswer.builder().id(11L).build())).isNull();
        verifyNoInteractions(unitDefinitionRepository);
    }

    private static UnitDefinition metre(Long id) {
        UnitDefinition unit = new UnitDefinition();
        unit.setId(id);
        unit.setLabel("Mètre");
        unit.setSymbol("m");
        unit.setDimension(UnitDefinition.Dimension.LENGTH);
        unit.setFactorToBase(1.0);
        return unit;
    }

}
