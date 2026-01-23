package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
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
import static org.mockito.ArgumentMatchers.any;
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

    Page<Specimen> p ;
    Pageable pageable;
    Specimen unit1;
    Specimen unit2;
    RecordingUnit u;
    Institution institution;

    @BeforeEach
    void setUp() {
        unit1 = new Specimen();
        unit2 = new Specimen();
        u = new RecordingUnit();
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
    void loadSpecimen_Success() {

        lazyModel = new SpecimenInRecordingUnitLazyDataModel(specimenService,sessionSettingsBean,langBean, u);

        // Arrange
        when(specimenService.findAllByInstitutionAndByRecordingUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<Specimen> actualResult = lazyModel.loadSpecimens("null", new Long[2], new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(unit1, actualResult.getContent().get(0));
        assertEquals(unit2, actualResult.getContent().get(1));
    }
}