package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.FieldCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FieldServiceTest {

    private FieldService fieldService;

    @BeforeEach
    void setUp() {
        fieldService = new FieldService();
    }

    @Test
    void searchAllFieldCodes() {
        List<String> fieldCodes = fieldService.searchAllFieldCodes();

        assertThat(fieldCodes).isNotEmpty();
    }

    @Test
    void findFieldCodesOf_shouldReturnFieldCodes_ofClass() {
        List<String> results = fieldService.findFieldCodesOf(TestClass.class);
        assertThat(results).containsExactlyInAnyOrder("CODE1", "CODE2");
    }

    private static class TestClass {
        @FieldCode
        public static final String FIELD_CODE_1 = "code1";

        @FieldCode
        public static final String FIELD_CODE_2 = "code2";

        private final String NOT_A_FIELD_CODE = "notAFieldCode";
    }

}