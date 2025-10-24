package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PanelsConfig {
    @Bean
    AbstractSingleEntity.Deps singleEntityDeps(
            SessionSettingsBean sessionSettingsBean,
            FieldConfigurationService fieldConfigurationService,
            SpatialUnitTreeService spatialUnitTreeService,
            SpatialUnitService spatialUnitService,
            ActionUnitService actionUnitService,
            DocumentService documentService,
            ApplicationContext applicationContext) {
        return new AbstractSingleEntity.Deps(
                sessionSettingsBean, fieldConfigurationService, spatialUnitTreeService, spatialUnitService, actionUnitService, documentService, applicationContext
        );
    }
}
