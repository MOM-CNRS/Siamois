package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.services.study.StudyService;
import fr.siamois.dto.entity.StudyDTO;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StudyPanel extends AbstractEntityPanel<StudyDTO> {

    private final transient StudyService studyService;

    public StudyPanel(ApplicationContext context) {
        super("common.entity.study", "bi bi-clipboard-data", "siamois-panel study-panel single-panel",
                "/resources/img/svg/clipboard-data.svg", "study", "study", context);
        this.studyService = context.getBean(StudyService.class);
    }

    @Override
    protected StudyDTO loadUnit(Long id) {
        return studyService.findById(id);
    }

    @Override
    protected String titleOf(StudyDTO unit) {
        return unit.getIdentifier();
    }

    // A panel is identified by its resource URI, which AbstractPanel already compares.
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
