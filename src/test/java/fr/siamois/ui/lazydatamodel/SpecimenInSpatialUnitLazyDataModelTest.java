package fr.siamois.ui.lazydatamodel;


import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
class SpecimenInSpatialUnitLazyDataModelTest {

    @Mock
    private SpecimenService specimenService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private SpecimenInSpatialUnitLazyDataModel lazyModel;

    Page<SpecimenDTO> p ;
    Pageable pageable;
    SpecimenDTO unit1;
    SpecimenDTO unit2;
    SpatialUnitDTO u;
    InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        unit1 = new SpecimenDTO();
        unit2 = new SpecimenDTO();
        u = new SpatialUnitDTO();
        u.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setFullIdentifier("Unit 1");
        unit2.setId(2L);
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
    }

    @Test
    @Disabled
    void loadActionUnits_Success() {

        lazyModel = new SpecimenInSpatialUnitLazyDataModel(specimenService,langBean, u);

        // Arrange
        when(specimenService.findAllBySpatialUnitAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<SpecimenDTO> actualResult = lazyModel.loadSpecimens("null", new Long[2], new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(unit1, actualResult.getContent().get(0));
        assertEquals(unit2, actualResult.getContent().get(1));
    }
}