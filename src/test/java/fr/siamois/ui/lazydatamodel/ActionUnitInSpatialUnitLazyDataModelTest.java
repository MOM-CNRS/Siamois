package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
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

    Page<ActionUnitDTO> p ;
    Pageable pageable;
    ActionUnitDTO unit1;
    ActionUnitDTO unit2;
    SpatialUnitDTO su;
    InstitutionDTO institution;

    @BeforeEach
    void setUp() {
        unit1 = new ActionUnitDTO();
        unit2 = new ActionUnitDTO();
        su = new SpatialUnitDTO();
        su.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setName("Unit 1");
        unit2.setId(2L);
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
    }

}