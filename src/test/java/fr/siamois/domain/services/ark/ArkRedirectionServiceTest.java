package fr.siamois.domain.services.ark;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.ark.Ark;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.infrastructure.database.repositories.ArkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArkRedirectionServiceTest {

    @Mock private ArkRepository arkRepository;
    @Mock private SpatialUnitService spatialUnitService;
    @Mock private ActionUnitService actionUnitService;
    @Mock private ServletUriComponentsBuilder builder;
    @Mock private ServletUriComponentsBuilder builder2;
    @Mock private UriComponents uriComponents;

    private ArkRedirectionService arkRedirectionService;

    @BeforeEach
    void setUp() {
        arkRedirectionService = new ArkRedirectionService(arkRepository, spatialUnitService, actionUnitService, builder);
    }

    @Test
    void getResourceUriFromArk_shouldReturnEmpty_whenNoResourceMatch() {
        String naan = "666666";
        String qualifier = "121212AE";

        when(arkRepository.findByNaanAndQualifier(naan, qualifier)).thenReturn(Optional.empty());

        Optional<URI> result = arkRedirectionService.getResourceUriFromArk(naan, qualifier);

        assertThat(result).isEmpty();
    }

    @Test
    void getResourceUriFromArk_shouldReturnSpatialUnitPath() {
        String naan = "666666";
        String qualifier = "121212AE";

        Ark ark = createArk(12L,  qualifier);

        SpatialUnit spatialUnit = new SpatialUnit();
        spatialUnit.setArk(ark);
        spatialUnit.setId(1L);

        when(arkRepository.findByNaanAndQualifier(naan, qualifier)).thenReturn(Optional.of(ark));
        when(spatialUnitService.findByArk(ark)).thenReturn(Optional.of(spatialUnit));
        setupBuilders();

        Optional<URI> result = arkRedirectionService.getResourceUriFromArk(naan, qualifier);

        assertThat(result).isPresent();

        verify(builder2).path("/spatialunit");
        verify(builder2).queryParam("id", 1L);
    }

    private static Ark createArk(long id, String qualifier) {
        Ark ark = new Ark();
        ark.setInternalId(id);
        ark.setQualifier(qualifier);

        Institution institution = new Institution();
        institution.setId(id);

        ark.setCreatingInstitution(institution);
        return ark;
    }

    @Test
    void getResourceUriFromArk_shouldReturnActionUnitPath() {
        String naan = "666666";
        String qualifier = "121212AE";

        Ark ark = createArk(10L,  qualifier);

        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(12L);

        when(arkRepository.findByNaanAndQualifier(naan, qualifier)).thenReturn(Optional.of(ark));
        when(actionUnitService.findByArk(ark)).thenReturn(Optional.of(actionUnit));
        setupBuilders();

        Optional<URI> result = arkRedirectionService.getResourceUriFromArk(naan, qualifier);

        assertThat(result).isPresent();

        verify(builder2).path("/actionunit");
        verify(builder2).queryParam("id", 12L);
    }

    private void setupBuilders() {
        when(builder.cloneBuilder()).thenReturn(builder2);
        when(builder2.build()).thenReturn(uriComponents);
        when(uriComponents.toUri()).thenReturn(URI.create("http://localhost"));
        when(builder2.path(anyString())).thenReturn(builder2);
        when(builder2.queryParam(anyString(), Optional.ofNullable(any()))).thenReturn(builder2);
    }

}