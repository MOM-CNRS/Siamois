package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialUnitParentsLazyDataModelTest {

    @Mock
    private SpatialUnitService spatialUnitService;

    @Mock
    private LangBean langBean;

    @InjectMocks
    private SpatialUnitParentsLazyDataModel lazyModel;

    Page<SpatialUnitDTO> p ;
    Pageable pageable;
    SpatialUnitDTO spatialUnit1;
    SpatialUnitDTO spatialUnit2;
    SpatialUnitDTO spatialUnit3;
    InstitutionDTO institution;


    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnitDTO();
        spatialUnit2 = new SpatialUnitDTO();
        spatialUnit3 = new SpatialUnitDTO();
        institution = new InstitutionDTO();
        institution.setId(1L);
        spatialUnit1.setId(1L);
        spatialUnit2.setId(2L);
        p = new PageImpl<>(List.of(spatialUnit1, spatialUnit2));
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void loadSpatialUnits() {

        lazyModel = new SpatialUnitParentsLazyDataModel(spatialUnitService,langBean, spatialUnit3);

        // Arrange
        when(spatialUnitService.findAllByChildAndByNameContainingAndByCategoriesAndByGlobalContaining(
                ArgumentMatchers.any(SpatialUnitDTO.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long[].class),
                ArgumentMatchers.any(Long[].class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(p);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<SpatialUnitDTO> actualResult = lazyModel.loadSpatialUnits("null", new Long[2],new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(spatialUnit1, actualResult.getContent().get(0));
        assertEquals(spatialUnit2, actualResult.getContent().get(1));
    }
}