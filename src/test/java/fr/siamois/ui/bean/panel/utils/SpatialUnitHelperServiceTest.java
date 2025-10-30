package fr.siamois.ui.bean.panel.utils;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.PrimeFaces;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.AssertionErrors.assertTrue;

@ExtendWith(MockitoExtension.class)
class SpatialUnitHelperServiceTest {

    @Mock
    private SpatialUnitService spatialUnitService;

    @InjectMocks
    private SpatialUnitHelperService spatialUnitHelperService;

    private PrimeFaces mockPrimeFaces;

    @Mock
    private FacesContext facesContext;


    @BeforeEach
    void setUp() {

        // Mock PrimeFaces
        mockPrimeFaces = mock(PrimeFaces.class);
        PrimeFaces.setCurrent(mockPrimeFaces);

    }

    @Test
    void testReinitialize() {
        Consumer<SpatialUnit> spatialUnitSetter = mock(Consumer.class);
        Consumer<String> spatialUnitErrorMessageSetter = mock(Consumer.class);
        Consumer<String> spatialUnitListErrorMessageSetter = mock(Consumer.class);
        Consumer<List<SpatialUnit>> spatialUnitListSetter = mock(Consumer.class);
        Consumer<List<SpatialUnit>> spatialUnitParentsListSetter = mock(Consumer.class);
        Consumer<String> spatialUnitParentsListErrorMessageSetter = mock(Consumer.class);

        spatialUnitHelperService.reinitialize(
                spatialUnitSetter,
                spatialUnitErrorMessageSetter,
                spatialUnitListErrorMessageSetter,
                spatialUnitListSetter,
                spatialUnitParentsListSetter,
                spatialUnitParentsListErrorMessageSetter
        );

        verify(spatialUnitSetter).accept(null);
        verify(spatialUnitErrorMessageSetter).accept(null);
        verify(spatialUnitListErrorMessageSetter).accept(null);
        verify(spatialUnitListSetter).accept(null);
        verify(spatialUnitParentsListSetter).accept(null);
        verify(spatialUnitParentsListErrorMessageSetter).accept(null);
    }

    @Test
    void testGetFirstThreeNull() {

        // Act
        List<String> result = spatialUnitHelperService.getFirstThree(null);

        // Assert
        assertEquals(0, result.size());
    }

    @Test
    void testGetFirstThreeWithLessThanThreeItems() {
        // Arrange
        Set<String> testSet = new HashSet<>();
        testSet.add("Item 1");
        testSet.add("Item 2");

        // Act
        List<String> result = spatialUnitHelperService.getFirstThree(testSet);

        // Assert
        assertEquals(2, result.size());
        assertTrue("Result should contain Item 1", result.contains("Item 1"));
        assertTrue("Result should contain Item 2", result.contains("Item 2"));
    }

    @Test
    void testGetFirstThreeWithMoreThanThreeItems() {
        // Arrange - use LinkedHashSet to guarantee insertion order for testing
        Set<String> testSet = new LinkedHashSet<>();
        testSet.add("Item 1");
        testSet.add("Item 2");
        testSet.add("Item 3");
        testSet.add("Item 4");
        testSet.add("Item 5");

        // Act
        List<String> result = spatialUnitHelperService.getFirstThree(testSet);

        // Assert
        assertEquals(3, result.size());
        // Since sets don't guarantee order, we just verify that the result has 3 items from the original set
        for (String item : result) {
            assertTrue("Result items should be from the original set", testSet.contains(item));
        }
    }

}
