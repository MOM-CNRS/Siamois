package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.InstitutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationWriteAuthorizerTest {

    @Mock ProfilePermissionService profilePermissionService;
    @Mock InstitutionMapper institutionMapper;

    private RelationWriteAuthorizer authorizer;
    private final PersonDTO person = new PersonDTO();
    private final InstitutionDTO institutionDto = new InstitutionDTO();

    @BeforeEach
    void setUp() {
        authorizer = new RelationWriteAuthorizer(profilePermissionService, institutionMapper);
        person.setId(5L);
        when(institutionMapper.convert(any(Institution.class))).thenReturn(institutionDto);
    }

    private RelationEndpoint unit() {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(1L);
        unit.setCreatedByInstitution(new Institution());
        return new RelationEndpoint(RelationEntityKind.RECORDING_UNIT, 1L, unit, 7L, null, 1L);
    }

    @Test
    void aRecordingUnitIsEditedWithTheRecordingUnitPermissionsOfItsProject() {
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(7L),
                eq(PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS), eq(PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS),
                eq(PermissionConstants.PROJECT_EDIT_RECORDING_UNITS))).thenReturn(true);

        assertThat(authorizer.canEdit(unit(), person)).isTrue();

        ArgumentCaptor<UserInfo> user = ArgumentCaptor.forClass(UserInfo.class);
        verify(profilePermissionService).hasProjectPermission(user.capture(), eq(7L), any(), any(), any());
        assertThat(user.getValue().getUser()).isSameAs(person);
        assertThat(user.getValue().getInstitution()).isSameAs(institutionDto);
    }

    @Test
    void withoutThePermissionTheEditIsRefused() {
        assertThat(authorizer.canEdit(unit(), person)).isFalse();
    }

    @Test
    void aPersonIsNeverEditedThroughALink() {
        RelationEndpoint endpoint = new RelationEndpoint(RelationEntityKind.PERSON, 2L, new fr.siamois.domain.models.auth.Person(), null, null, null);

        assertThat(authorizer.canEdit(endpoint, person)).isFalse();
    }

    @Test
    void anonymousCallersAreRefused() {
        assertThat(authorizer.canEdit(unit(), null)).isFalse();
        assertThat(authorizer.canEdit(null, person)).isFalse();
        assertThat(authorizer.canEdit(unit(), new PersonDTO())).isFalse();
    }

    @Test
    void aPlaceNeedsThePlacesPermissionOfTheOrganisation() {
        fr.siamois.domain.models.spatialunit.SpatialUnit place = new fr.siamois.domain.models.spatialunit.SpatialUnit();
        place.setCreatedByInstitution(new Institution());
        RelationEndpoint endpoint = new RelationEndpoint(RelationEntityKind.SPATIAL_UNIT, 3L, place, null, null, 1L);
        when(profilePermissionService.hasOrganizationPermission(person, institutionDto, PermissionConstants.ORGANIZATION_MANAGE_PLACES))
                .thenReturn(true);

        assertThat(authorizer.canEdit(endpoint, person)).isTrue();
    }

    @Test
    void aProjectNeedsTheProjectWritePermission() {
        fr.siamois.domain.models.actionunit.ActionUnit project = new fr.siamois.domain.models.actionunit.ActionUnit();
        project.setCreatedByInstitution(new Institution());
        RelationEndpoint endpoint = new RelationEndpoint(RelationEntityKind.ACTION_UNIT, 4L, project, 4L, null, 1L);
        when(profilePermissionService.hasActionUnitWritePermission(person, institutionDto, 4L)).thenReturn(true);

        assertThat(authorizer.canEdit(endpoint, person)).isTrue();
    }
}
