package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
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
class ActionUnitInSpatialUnitLazyDataModelTest {

    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private ActionUnitInSpatialUnitLazyDataModel lazyModel;

    Page<ActionUnit> p ;
    Pageable pageable;
    ActionUnit unit1;
    ActionUnit unit2;
    SpatialUnit su;
    Institution institution;

    @BeforeEach
    void setUp() {
        unit1 = new ActionUnit();
        unit2 = new ActionUnit();
        su = new SpatialUnit();
        su.setId(1L);
        institution = new Institution();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setName("Unit 1");
        unit2.setId(2L);
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void loadActionUnits_Success() {

        lazyModel = new ActionUnitInSpatialUnitLazyDataModel(actionUnitService,sessionSettingsBean,langBean, su);

        // Arrange
        when(actionUnitService.findAllByInstitutionAndBySpatialUnitAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(p);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<ActionUnit> actualResult = lazyModel.loadActionUnits("null", new Long[2], new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(unit1, actualResult.getContent().get(0));
        assertEquals(unit2, actualResult.getContent().get(1));
    }
}