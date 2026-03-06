package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import org.springframework.stereotype.Component;

@Component
public class SpatialUnitWriteVerifier implements WritePermissionVerifier {

    private final InstitutionService institutionService;

    public SpatialUnitWriteVerifier(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @Override
    public Class<? extends TraceableEntity> getEntityClass() {
        return SpatialUnit.class;
    }

    @Override
    public boolean hasSpecificWritePermission(UserInfo userInfo, AbstractEntityDTO resource) {
        return institutionService.personIsInstitutionManagerOrActionManager(userInfo.getUser(), userInfo.getInstitution());
    }
}
