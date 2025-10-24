package fr.siamois.ui.bean.field;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>This bean handles the creation of new Spatial Unit</p>
 *
 * @author Julien Linget
 */
@Getter
@Setter
@Slf4j
@Component
@SessionScoped
public class SpatialUnitFieldBean implements Serializable {

    // Injections
    private final transient FieldService fieldService;
    private final LangBean langBean;
    private final SessionSettingsBean sessionSettingsBean;
    private final transient SpatialUnitService spatialUnitService;
    private final transient ConceptService conceptService;
    private final transient FieldConfigurationService fieldConfigurationService;
    private final RedirectBean redirectBean;
    private final LabelBean labelBean;

    // Storage
    private List<SpatialUnit> refSpatialUnits = new ArrayList<>();
    private List<String> labels;
    private List<Concept> concepts;

    // Fields
    private Concept selectedConcept = null;
    private String fName = "";
    private List<SpatialUnit> fParentsSpatialUnits = new ArrayList<>();
    private List<SpatialUnit> fChildrenSpatialUnits = new ArrayList<>();

    public SpatialUnitFieldBean(FieldService fieldService,
                                LangBean langBean,
                                SessionSettingsBean sessionSettingsBean,
                                SpatialUnitService spatialUnitService,
                                ConceptService conceptService,
                                FieldConfigurationService fieldConfigurationService,
                                RedirectBean redirectBean, LabelBean labelBean) {
        this.fieldService = fieldService;
        this.langBean = langBean;
        this.sessionSettingsBean = sessionSettingsBean;
        this.spatialUnitService = spatialUnitService;
        this.conceptService = conceptService;
        this.fieldConfigurationService = fieldConfigurationService;
        this.redirectBean = redirectBean;
        this.labelBean = labelBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        fName = "";
        selectedConcept = null;
        fParentsSpatialUnits = new ArrayList<>();
        fChildrenSpatialUnits = new ArrayList<>();
    }

    /**
     * Called on page rendering.
     * Reset all fields.
     */
    public void init() {
        init(new ArrayList<>(), new ArrayList<>());
        refSpatialUnits = spatialUnitService.findAllOfInstitution(sessionSettingsBean.getSelectedInstitution());
        labels = refSpatialUnits.stream()
                .map(SpatialUnit::getName)
                .toList();
        concepts = null;
        selectedConcept = null;
        fName = "";
        fParentsSpatialUnits = new ArrayList<>();
    }

    public void init(List<SpatialUnit> parents, List<SpatialUnit> children) {
        refSpatialUnits = spatialUnitService.findAllOfInstitution(sessionSettingsBean.getSelectedInstitution());
        labels = refSpatialUnits.stream()
                .map(SpatialUnit::getName)
                .toList();
        concepts = null;
        selectedConcept = null;
        fName = "";
        fParentsSpatialUnits = parents;
        fChildrenSpatialUnits = children;
    }

    public String getUrlForFieldCode(String fieldCode) {
        return fieldConfigurationService.getUrlForFieldCode(sessionSettingsBean.getUserInfo(), fieldCode);
    }

    public String resolveCustomFieldLabel(CustomField f) {
        if(Boolean.TRUE.equals(f.getIsSystemField())) {
            return langBean.msg(f.getLabel());
        }
        return f.getLabel();
    }


    public String resolvePanelLabel(CustomFormPanel p) {
        if(Boolean.TRUE.equals(p.getIsSystemPanel())) {
            return langBean.msg(p.getName());
        }
        return p.getName();
    }

    /**
     * Is creation of new spatial units allowed?
     *
     * @return true if creation is allowed
     */
    public boolean isCreateAllowed() {
        return spatialUnitService.hasCreatePermission(sessionSettingsBean.getUserInfo());
    }
}
