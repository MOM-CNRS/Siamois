package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFieldServiceTest {

    @Mock
    private CustomFieldRepository customFieldRepository;

    @InjectMocks
    private CustomFieldService customFieldService;

    @Test
    void findAllFieldsBySpatialUnitId_success() {

        when(customFieldRepository.findAllFieldsBySpatialUnitId(anyLong()))
                .thenReturn(List.of(new CustomFieldSelectMultiple()));

        // act
        List<CustomField> res = customFieldService.findAllFieldsBySpatialUnitId(anyLong());
        assertNotNull(res);
        assertFalse(res.isEmpty());
        assertInstanceOf(CustomField.class, res.get(0));



    }

}