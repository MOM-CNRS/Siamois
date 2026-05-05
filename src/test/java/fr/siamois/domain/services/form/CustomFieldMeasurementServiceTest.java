package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldMeasurementRepository;
import fr.siamois.mapper.CustomFieldMeasurementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldMeasurementServiceTest {

    @Mock
    private CustomFieldMeasurementRepository repository;

    @Mock
    private CustomFieldMeasurementMapper mapper;

    @InjectMocks
    private CustomFieldMeasurementService service;

    @Test
    @DisplayName("Should successfully map and save a measurement field")
    void save_ShouldReturnSavedMeasurement() {
        // Given
        CustomFieldMeasurementDTO dto = new CustomFieldMeasurementDTO();
        CustomFieldMeasurement entity = new CustomFieldMeasurement();
        CustomFieldMeasurement savedEntity = new CustomFieldMeasurement();

        when(mapper.invertConvert(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(savedEntity);

        // When
        CustomFieldMeasurement result = service.save(dto);

        // Then
        assertNotNull(result);
        verify(mapper, times(1)).invertConvert(dto);
        verify(repository, times(1)).save(entity);
        assertEquals(savedEntity, result);
    }

    @Test
    @DisplayName("Should find a paginated list of measurements based on limit")
    void find_ShouldReturnPaginatedResults() {
        // Given
        int limit = 10;
        Pageable expectedPageable = PageRequest.of(0, limit);
        CustomFieldMeasurement measurement = new CustomFieldMeasurement();
        Page<CustomFieldMeasurement> expectedPage = new PageImpl<>(List.of(measurement));

        when(repository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // When
        Page<CustomFieldMeasurement> result = service.find(limit);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(repository, times(1)).findAll(expectedPageable);
    }
}