package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecimenInRecordingUnitLazyDataModelTest {

    @Mock
    private SpecimenService specimenService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private SpecimenInRecordingUnitLazyDataModel lazyModel;

    Page<SpecimenDTO> p ;
    Pageable pageable;
    SpecimenDTO unit1;
    SpecimenDTO unit2;
    RecordingUnitDTO u;
    Institution institution;

    @BeforeEach
    void setUp() {
        unit1 = new SpecimenDTO();
        unit2 = new SpecimenDTO();
        u = new RecordingUnitDTO();
        u.setId(1L);
        institution = new Institution();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setFullIdentifier("Unit 1");
        unit2.setId(2L);
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void loadSpecimens_Success() {
        // 1. Préparation des données de test
        InstitutionDTO mockInstitution = new InstitutionDTO();
        mockInstitution.setId(1L);  // ID spécifique pour éviter null

        RecordingUnitDTO mockRecordingUnit = new RecordingUnitDTO();
        mockRecordingUnit.setId(10L);  // ID spécifique

        SpecimenDTO specimen1 = new SpecimenDTO();
        SpecimenDTO specimen2 = new SpecimenDTO();
        Page<SpecimenDTO> mockPage = new PageImpl<>(List.of(specimen1, specimen2));

        // 2. Configuration des mocks avec les mêmes valeurs que celles utilisées dans le test
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(mockInstitution);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Utilisation de eq() pour tous les paramètres importants
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L),                     // institutionId - doit correspondre à mockInstitution.getId()
                eq(10L),                    // recordingUnitId - doit correspondre à mockRecordingUnit.getId()
                eq("null"),                 // fullIdentifierFilter
                any(Long[].class),          // categoryIds
                eq("null"),                 // globalFilter
                eq("en"),                   // langCode
                any(Pageable.class)         // pageable - plus flexible
        )).thenReturn(mockPage);

        // 3. Initialisation du modèle
        lazyModel = new SpecimenInRecordingUnitLazyDataModel(
                specimenService,
                sessionSettingsBean,
                langBean,
                mockRecordingUnit
        );

        // 4. Exécution de la méthode avec les mêmes paramètres que dans le mock
        Page<SpecimenDTO> result = lazyModel.loadSpecimens(
                "null",
                new Long[]{1L, 2L},  // categoryIds
                new Long[]{3L, 4L},  // personIds (non utilisé mais nécessaire)
                "null",
                PageRequest.of(0, 10)  // doit correspondre au pageable utilisé dans le mock
        );

        // 5. Assertions
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(specimen1, result.getContent().get(0));
        assertEquals(specimen2, result.getContent().get(1));

        // 6. Vérification des appels
        verify(sessionSettingsBean).getSelectedInstitution();
        verify(langBean).getLanguageCode();
        verify(specimenService).findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                eq(1L), eq(10L), eq("null"), any(Long[].class), eq("null"), eq("en"), any(Pageable.class)
        );
    }

}