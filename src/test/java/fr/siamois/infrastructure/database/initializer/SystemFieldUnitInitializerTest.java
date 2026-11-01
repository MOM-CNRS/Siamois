package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemFieldUnitInitializerTest {

    private static final long Z_INF_ID = -322L;
    private static final long SPECIMEN_WEIGHT_ID = -420L;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private UnitDefinitionService unitDefinitionService;

    @InjectMocks
    private SystemFieldUnitInitializer initializer;

    private static CustomFieldMeasurement row(long id, UnitDefinition unit) {
        CustomFieldMeasurement row = CustomFieldMeasurement.builder().id(id).isSystemField(true).build();
        row.setUnit(unit);
        return row;
    }

    private static UnitDefinition catalogueUnit(long id, String symbol, UnitDefinition.Dimension dimension) {
        return new UnitDefinition(id, null, symbol, symbol, dimension, 1.0, true);
    }

    @Test
    void initialize_shouldGiveAUnitlessMeasurementRowTheUnitItsDefinitionDeclares() throws DatabaseDataInitException {
        CustomFieldMeasurement zInf = row(Z_INF_ID, null);
        UnitDefinition metre = catalogueUnit(1L, "m", UnitDefinition.Dimension.LENGTH);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(zInf));
        when(unitDefinitionService.resolve(argThat(u -> u != null && "m".equals(u.getSymbol())
                && u.getDimension() == UnitDefinition.Dimension.LENGTH))).thenReturn(metre);

        initializer.initialize();

        assertThat(zInf.getUnit()).isSameAs(metre);
        verify(customFieldRepository).save(zInf);
    }

    @Test
    void initialize_shouldKeepAUnitAlreadyChosen() throws DatabaseDataInitException {
        UnitDefinition gram = catalogueUnit(10L, "g", UnitDefinition.Dimension.MASS);
        CustomFieldMeasurement weight = row(SPECIMEN_WEIGHT_ID, gram);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(weight));

        initializer.initialize();

        assertThat(weight.getUnit()).isSameAs(gram);
        verify(customFieldRepository, never()).save(any(CustomField.class));
        verifyNoInteractions(unitDefinitionService);
    }

    @Test
    void initialize_shouldFailWhenTheDeclaredUnitIsNotSeeded() {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(row(Z_INF_ID, null)));
        when(unitDefinitionService.resolve(any())).thenThrow(new IllegalStateException("no unit"));

        assertThatThrownBy(() -> initializer.initialize()).isInstanceOf(DatabaseDataInitException.class);
    }
}
