package fr.siamois.domain.services.attributeconverter;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customform.DependsOnJson;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFormLayoutConverterTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CustomFormLayoutConverter converter;

    @BeforeEach
    void setUp() {
        lenient().when(applicationContext.getBean(CustomFieldRepository.class)).thenReturn(customFieldRepository);
        lenient().when(applicationContext.getBean(EntityManager.class)).thenReturn(entityManager);
    }

    @Test
    void convertToDatabaseColumn_shouldSerializeDependsOnSpec() {
        CustomField field = CustomFieldText.builder().id(1L).build();
        DependsOnJson dependsOn = new DependsOnJson();
        dependsOn.setFieldId(2L);

        CustomCol col = new CustomCol.Builder()
                .field(field)
                .dependsOnSpec(dependsOn)
                .build();
        CustomRow row = new CustomRow.Builder().addColumn(col).build();
        CustomFormPanel panel = new CustomFormPanel.Builder().addRow(row).build();

        String json = converter.convertToDatabaseColumn(List.of(panel));

        assertTrue(json.contains("\"dependsOn\""));
        assertTrue(json.contains("\"fieldId\":2"));
    }

    @Test
    void convertToEntityAttribute_shouldDeserializeDependsOnSpec() {
        CustomField field = CustomFieldText.builder().id(1L).build();
        when(customFieldRepository.findById(1L)).thenReturn(Optional.of(field));

        String json = "[{\"className\":\"panel\",\"name\":\"p\",\"rows\":[{\"columns\":[" +
                "{\"className\":\"col\",\"isRequired\":false,\"isReadOnly\":false,\"fieldId\":1," +
                "\"dependsOn\":{\"fieldId\":2}}]}]}]";

        List<CustomFormPanel> panels = converter.convertToEntityAttribute(json);

        CustomCol col = panels.get(0).getRows().get(0).getColumns().get(0);
        assertEquals(2L, col.getDependsOnSpec().getFieldId());
    }
}
