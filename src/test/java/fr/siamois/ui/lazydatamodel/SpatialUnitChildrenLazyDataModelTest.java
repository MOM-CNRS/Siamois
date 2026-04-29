package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpatialUnitChildrenLazyDataModelTest {

    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @InjectMocks
    private SpatialUnitChildrenLazyDataModel lazyModel;

    Pageable pageable;

    Page<SpatialUnitDTO> pageDto ;
    SpatialUnitDTO spatialUnit1Dto;
    SpatialUnitDTO spatialUnit2Dto;
    SpatialUnitDTO spatialUnit3Dto;
    InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {

        pageable = PageRequest.of(0, 10);
        spatialUnit1Dto = new SpatialUnitDTO();
        spatialUnit2Dto = new SpatialUnitDTO();
        spatialUnit3Dto = new SpatialUnitDTO();
        institutionDto = new InstitutionDTO();
        institutionDto.setId(1L);
        spatialUnit1Dto.setId(1L);
        spatialUnit2Dto.setId(2L);
        pageDto = new PageImpl<>(List.of(spatialUnit1Dto, spatialUnit2Dto));
    }

    @Test
    @Disabled
    void loadSpatialUnits() {

        lazyModel = new SpatialUnitChildrenLazyDataModel(spatialUnitService,langBean, spatialUnit3Dto);

        // Arrange
        when(spatialUnitService.findAllByParentAndByNameContainingAndByCategoriesAndByGlobalContaining(
                ArgumentMatchers.any(SpatialUnitDTO.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(Long[].class),
                ArgumentMatchers.any(Long[].class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(String.class),
                ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(pageDto);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        // Page<SpatialUnitDTO> actualResult = lazyModel.loadSpatialUnits("null", new Long[2], new Long[2],"null", pageable);

        // Assert
        // Assert
        // assertEquals(spatialUnit1Dto, actualResult.getContent().get(0));
        // assertEquals(spatialUnit2Dto, actualResult.getContent().get(1));
    }
}