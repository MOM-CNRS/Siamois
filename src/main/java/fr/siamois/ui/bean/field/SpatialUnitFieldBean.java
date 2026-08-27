package fr.siamois.ui.bean.field;

import fr.siamois.annotations.ExecutionTimeLogger;
import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
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
    private List<SpatialUnitDTO> refSpatialUnits = new ArrayList<>();
    private List<String> labels;
    private List<ConceptDTO> concepts;

    // Fields
    private Concept selectedConcept = null;
    private String fName = "";
    private List<SpatialUnitDTO> fParentsSpatialUnits = new ArrayList<>();
    private List<SpatialUnitDTO> fChildrenSpatialUnits = new ArrayList<>();

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
        refSpatialUnits = spatialUnitService.findAllOfInstitution(sessionSettingsBean.getSelectedInstitution().getId());
        labels = refSpatialUnits.stream()
                .map(SpatialUnitDTO::getName)
                .toList();
        concepts = null;
        selectedConcept = null;
        fName = "";
        fParentsSpatialUnits = new ArrayList<>();
    }

    public void init(List<SpatialUnitDTO> parents, List<SpatialUnitDTO> children) {
        refSpatialUnits = spatialUnitService.findAllOfInstitution(sessionSettingsBean.getSelectedInstitution().getId());
        labels = refSpatialUnits.stream()
                .map(SpatialUnitDTO::getName)
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
     * Same as {@link #getUrlForFieldCode(String)} but checks for a project-level (Action Unit)
     * thesaurus override first, falling back to the institution configuration.
     *
     * @param actionUnitId the current project's id, or null if the field isn't project-scoped
     */
    public String getUrlForFieldCode(String fieldCode, Long actionUnitId) {
        return fieldConfigurationService.getUrlForFieldCode(sessionSettingsBean.getUserInfo(), fieldCode, actionUnitId);
    }

    /**
     * The OpenTheso URL to display for a field's autocomplete, safe to call for any field —
     * {@code concept.xhtml} calls this with whatever field it's showing, which can be a plain
     * {@code CustomFieldSelectOne}/{@code CustomFieldSelectMultiple} additional field that isn't a
     * concept field at all. EL's {@code BeanELResolver} throws {@code PropertyNotFoundException} for a
     * missing bean property regardless of where the expression is used — plain attribute binding or
     * method-call argument alike — so the {@code instanceof} check has to happen here instead of in the
     * view. Delegates to {@link FieldConfigurationService#getUrlForConceptField(CustomFieldConcept, Long)},
     * which resolves the field's branch/collection restriction for the project, falling back to its
     * field-code configuration.
     *
     * @param field        the field to look the edit URL up for
     * @param actionUnitId the current project's id, or null if the field isn't project-scoped
     * @return the edit URL, or null if the field isn't a concept field or has no configuration
     */
    public String getUrlForField(CustomField field, Long actionUnitId) {
        return field instanceof CustomFieldConcept conceptField
                ? fieldConfigurationService.getUrlForConceptField(conceptField, actionUnitId)
                : null;
    }

    /**
     * The field code driving a concept field, or null when the field isn't field-code-driven (e.g. a
     * plain {@code CustomFieldSelectOne}/{@code CustomFieldSelectMultiple} additional field) — see
     * {@link #getUrlForField(CustomField, Long)} for why this can't just be a {@code .fieldCode} EL
     * property access in the view.
     */
    public String resolveFieldCode(CustomField field) {
        return field instanceof CustomFieldConceptFromFieldCode fromFieldCode ? fromFieldCode.getFieldCode() : null;
    }

    /**
     * Fetch the autocomplete results on API for the selected field and add them to the list of concepts.
     * Optionally sorts the results by root group if the "sortByParent" component attribute is true.
     *
     * @param input the input of the user
     * @return the list of concepts that match the input to display in the autocomplete
     */
    @ExecutionTimeLogger
    public List<ConceptAutocompleteDTO> completeWithFieldCode(String input) {
        String fieldCode = "Undefined";
        Object fieldAttr = null;
        try {
            FacesContext context = FacesContext.getCurrentInstance();
            fieldCode = (String) UIComponent.getCurrentComponent(context).getAttributes().get("fieldCode");
            fieldAttr = UIComponent.getCurrentComponent(context).getAttributes().get("field");

            // Retrieve the sortByParent attribute from the current UI component
            Boolean sortByParent = Boolean.parseBoolean(
                    (String) UIComponent.getCurrentComponent(context).getAttributes().get("sortByParent")
            );

            // If the field depends on another field's value, restrict the search to the concepts
            // matching this field's code AND related to that base value.
            Concept dependsOnBaseConcept = (Concept) UIComponent.getCurrentComponent(context)
                    .getAttributes().get("dependsOnBaseConcept");

            // Project (Action Unit) scope, if the field belongs to a project-scoped entity's form;
            // null for institution-level entities, which keeps the institution-only lookup.
            Long actionUnitId = (Long) UIComponent.getCurrentComponent(context)
                    .getAttributes().get("actionUnitId");

            // A field's own branch/collection restriction (set through the project's field-settings
            // drawer) takes priority over its field-code configuration; fetchAutocomplete(CustomFieldConcept, ...)
            // already falls back to the field-code lookup on its own when the field carries no such
            // restriction, so this only needs to pick which overload to call.
            List<ConceptAutocompleteDTO> results;
            if (dependsOnBaseConcept != null) {
                results = fieldConfigurationService.fetchAutocompleteRelated(
                        sessionSettingsBean.getUserInfo(), fieldCode, dependsOnBaseConcept, input, actionUnitId);
            } else if (fieldAttr instanceof CustomFieldConcept conceptField) {
                results = fieldConfigurationService.fetchAutocomplete(conceptField, input, actionUnitId);
            } else {
                results = fieldConfigurationService.fetchAutocomplete(
                        sessionSettingsBean.getUserInfo(), fieldCode, input, actionUnitId);
            }

            if (sortByParent != null && sortByParent) {
                // Sort the results by root group (using the getRootGroup method)
                results.sort(Comparator.comparing(
                        this::getRootGroup,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ));
            }

            return results;
        }
        catch (NoConfigForFieldException e) {
            displayErrorMessage(langBean, "common.error.thesaurus.noConfigForField", fieldCode);
            return List.of();
        }
        catch (ResourceAccessException e) {
            displayErrorMessage(langBean, "common.error.thesaurus.resourceAccess", fieldCode);
            return List.of();
        }
        catch (IllegalStateException e) {
            // Thrown by FieldConfigurationService#fetchAutocomplete(CustomFieldConcept, ...) when the
            // field has neither a branch/collection restriction nor a field code to fall back on —
            // i.e. an additional concept field nobody has configured a vocabulary source for yet.
            // It has no field code to identify itself with in the message, so use its label instead.
            String identifier = fieldAttr instanceof CustomField field ? resolveCustomFieldLabel(field) : fieldCode;
            displayErrorMessage(langBean, "common.error.thesaurus.field.noVocabulary", identifier);
            return List.of();
        }
        catch (Exception e) {
            displayErrorMessage(langBean, "common.error.thesaurus.field.exception", fieldCode);
            return List.of();
        }
    }



    /**
     * return the root label of the concept for autocomplete grouping
     *
     * @param dto The concept DTO
     * @return the root concept label
     */
    public String getRootGroup(ConceptAutocompleteDTO dto) {
        if (dto.getHierarchyPrefLabels() != null) {
            String hierarchy = dto.getHierarchyPrefLabels();
            return hierarchy.split("\n")[0].trim();
        }
        return dto.getOriginalPrefLabel();
    }

    public String resolveCustomFieldLabel(CustomField f) {
        if(Boolean.TRUE.equals(f.getIsSystemField())) {
            return langBean.msg(f.getLabel());
        }
        return f.getLabel();
    }


    public String resolvePanelLabel(CustomFormPanelUiDto p) {
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
