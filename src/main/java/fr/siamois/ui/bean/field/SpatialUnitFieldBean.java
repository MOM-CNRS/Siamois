package fr.siamois.ui.bean.field;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import javax.faces.bean.SessionScoped;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static fr.siamois.utils.MessageUtils.displayErrorMessage;

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
@RequiredArgsConstructor
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

    /**
     * Fetch the autocomplete results on API for the selected field and add them to the list of concepts.
     *
     * @param input the input of the user
     * @return the list of concepts that match the input to display in the autocomplete
     */
    public List<ConceptAutocompleteDTO> completeWithFieldCode(String input) {
        String fieldCode = "Undefined";
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            fieldCode = (String) UIComponent.getCurrentComponent(context).getAttributes().get("fieldCode");
            return fieldConfigurationService.fetchAutocomplete(sessionSettingsBean.getUserInfo(), fieldCode, input);
        }
        catch (NoConfigForFieldException e) {
            displayErrorMessage(langBean, "common.error.thesaurus.noConfigForField",fieldCode);
            return List.of();
        }
        catch(ResourceAccessException e) {
            displayErrorMessage(langBean, "common.error.thesaurus.resourceAccess",fieldCode);
            return List.of();
        }
        catch(Exception e) {
            displayErrorMessage(langBean, "common.error.thesaurus.field.exception",fieldCode);
            return List.of();
        }
    }

    public String resolveCustomFieldLabel(CustomField f) {
        if(Boolean.TRUE.equals(f.getIsSystemField())) {
            return langBean.msg(f.getLabel());
        }
        return f.getLabel();
    }


    public String resolvePanelLabel(CustomFormPanel p) {
        if(p == null) {
            return langBean.msg("common.panel.title.undefined");
        }
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
