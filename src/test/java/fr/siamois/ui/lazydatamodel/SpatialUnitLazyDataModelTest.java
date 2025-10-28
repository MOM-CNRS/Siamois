package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpatialUnitLazyDataModelTest {

    @Mock
    private SpatialUnitService spatialUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private SpatialUnitLazyDataModel lazyModel;

    Page<SpatialUnit> p ;
    Pageable pageable;
    SpatialUnit spatialUnit1;
    SpatialUnit spatialUnit2;
    Institution institution;


    @BeforeEach
    void setUp() {
        spatialUnit1 = new SpatialUnit();
        spatialUnit2 = new SpatialUnit();
        institution = new Institution();
        institution.setId(1L);
        spatialUnit1.setId(1L);
        spatialUnit1.setName("Unit 1");
        spatialUnit2.setId(2L);
        p = new PageImpl<>(List.of(spatialUnit1, spatialUnit2));
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void loadSpatialUnits_Success() {

        lazyModel = new SpatialUnitLazyDataModel(spatialUnitService,sessionSettingsBean,langBean);

        // Arrange
        when(spatialUnitService.findAllByInstitutionAndByNameContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(p);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<SpatialUnit> actualResult = lazyModel.loadSpatialUnits("null", new Long[2], new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(spatialUnit1, actualResult.getContent().get(0));
        assertEquals(spatialUnit2, actualResult.getContent().get(1));
    }

    @Test
    void load_FromCacheSuccess() {

        lazyModel = Mockito.spy(new SpatialUnitLazyDataModel(spatialUnitService,sessionSettingsBean,langBean));
        lazyModel.cachedFirst = 0;
        lazyModel.cachedPageSize = 10;
        Map<String, SortMeta> sortBy = new HashMap<>();
        Map<String, FilterMeta> filters = new HashMap<>();
        lazyModel.queryResult = p.getContent();

        // Arrange
        when(lazyModel.isFilterCriteriaSame(
                any(Map.class),
                any(Map.class)
        )).thenReturn(true);

        when(lazyModel.isSortCriteriaSame(
                any(Map.class),
                any(Map.class)
        )).thenReturn(true);

        // Act
        List<SpatialUnit> result = lazyModel.load(0, 10, sortBy, filters);

        // Assert loadSpatialUnit has not been called
        verify(lazyModel, never()).loadSpatialUnits(any(String.class),
                any(Long[].class),
                any(Long[].class),
                any(String.class),
                any(org.springframework.data.domain.Pageable.class));
        // Assert
        assertEquals(spatialUnit1, result.get(0));
        assertEquals(spatialUnit2, result.get(1));
    }

    @Test
    void testLoad_withCategoryFilterAndAscSort() {

        lazyModel = Mockito.spy(new SpatialUnitLazyDataModel(spatialUnitService,sessionSettingsBean,langBean));

        // Arrange
        int first = 20;
        int pageSize = 10;

        // Filter setup
        Concept concept = new Concept();
        concept.setId(1L);
        LocalizedConceptData label = new LocalizedConceptData();
        label.setConcept(concept);
        List<LocalizedConceptData> categoryLabels = List.of(label);
        Person pers = new Person();
        pers.setId(1L);
        List<Person> persons = List.of(pers);

        Map<String, FilterMeta> filters = new HashMap<>();


        FilterMeta globalFilter = FilterMeta.builder().field("global").filterValue("global").build();

        FilterMeta catFilter = FilterMeta.builder().field("category").filterValue(categoryLabels).build();
        FilterMeta authorFilter = FilterMeta.builder().field("author").filterValue(persons).build();
        FilterMeta nameFilter = FilterMeta.builder().field("name").filterValue("name").build();


        filters.put("category", catFilter);
        filters.put("name", nameFilter);
        filters.put("author", authorFilter);
        filters.put("globalFilter", globalFilter);

        // Sort setup
        SortMeta sortMeta =  SortMeta.builder().field("cat").order(SortOrder.ASCENDING).build();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put("category", sortMeta);
        sortBy.put("creationTime", sortMeta);
        SortMeta sortMeta2 = SortMeta.builder().field("author").order(SortOrder.DESCENDING).build();

        sortBy.put("author", sortMeta2);

        // Mock data
        List<SpatialUnit> spatialUnits = new ArrayList<>();
        spatialUnits.add(spatialUnit1);
        for (int i = 0; i < 29; i++) {
            spatialUnits.add(spatialUnit2);
        }

        Page<SpatialUnit> page = new PageImpl<>(spatialUnits);

        doReturn(page).when(lazyModel).loadSpatialUnits(
                any(), any(), any(), any()

                ,any(Pageable.class)
        );

        // Act
        List<SpatialUnit> result = lazyModel.load(first, pageSize, sortBy, filters);

        // Assert
        assertEquals(30, result.size());
        assertEquals("Unit 1", result.get(0).getName());
        verify(lazyModel).loadSpatialUnits(
                eq("name"), eq(new Long[]{1L}),eq(new Long[]{1L}), eq("global"), pageableCaptor.capture()
        );

        Pageable capturedPageable = pageableCaptor.getValue();

        Sort.Order order = capturedPageable.getSort().getOrderFor("c_label");
        assertNotNull(order);
        Sort.Order order2 = capturedPageable.getSort().getOrderFor("p_lastname");
        assertNotNull(order2);
        assertEquals(2, capturedPageable.getPageNumber());
        assertEquals(10, capturedPageable.getPageSize());
        assertEquals(Sort.Direction.ASC, order.getDirection());
        assertEquals(Sort.Direction.DESC, order2.getDirection());

        // also test paginator value getters

        int firstIndexOnPage = lazyModel.getFirstIndexOnPage();
        int lastIndexOnPage = lazyModel.getLastIndexOnPage();

        assertEquals(21, firstIndexOnPage);
        assertEquals(30, lastIndexOnPage);


    }


}