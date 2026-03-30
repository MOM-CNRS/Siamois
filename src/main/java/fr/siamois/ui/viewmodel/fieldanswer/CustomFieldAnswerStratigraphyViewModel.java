package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.primefaces.PrimeFaces;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

@Getter
@Setter
@NoArgsConstructor
public class CustomFieldAnswerStratigraphyViewModel extends CustomFieldAnswerViewModel {

        // The rels
        private transient Set<StratigraphicRelationshipDTO> anteriorRelationships = new HashSet<>();
        private transient Set<StratigraphicRelationshipDTO> posteriorRelationships = new HashSet<>();
        private transient Set<StratigraphicRelationshipDTO> synchronousRelationships = new HashSet<>();

        // Callbacks onadd et ondelete pour que le bean gère la sauvegarde
        private transient BiConsumer<FacesContext, UIComponent> onAdd;
        private transient Runnable onDelete;

        // New rel form
        private transient ConceptAutocompleteDTO conceptToAdd;
        private transient RecordingUnitSummaryDTO sourceToAdd = new RecordingUnitSummaryDTO(); // always the recording unit the panel is about
        private transient RecordingUnitSummaryDTO targetToAdd;
        private transient Boolean vocabularyDirectionToAdd = false; // always false in this version
        private transient Boolean isUncertainToAdd;

        // Displayed selected rel info
        private transient StratigraphicRelationshipDTO selectedRel;

        public void updateSelectedRel() {


                // Retrieve parameters from the request
                String sourceId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("source");
                String targetId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("target");
                String typeRel = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("typeRel");

                if (sourceId == null || targetId == null || typeRel == null) {
                        return;
                }

                // Determine which set to search based on typeRel
                Set<StratigraphicRelationshipDTO> relsToSearch = null;
                switch (typeRel.toLowerCase()) {
                        case "synchronous":
                                relsToSearch = synchronousRelationships;
                                break;
                        case "anterior":
                                relsToSearch = anteriorRelationships;
                                break;
                        case "posterior":
                                relsToSearch = posteriorRelationships;
                                break;
                        default:
                                return;
                }

                // Search for the relationship
                for (StratigraphicRelationshipDTO rel : relsToSearch) {
                        RecordingUnitSummaryDTO relUnit1 = rel.getUnit1();
                        RecordingUnitSummaryDTO relUnit2 = rel.getUnit2();

                        // Check if the source and target match (order doesn't matter)
                        boolean sourceMatches = (relUnit1.getFullIdentifier().equals(sourceId) && relUnit2.getFullIdentifier().equals(targetId)) ||
                                (relUnit1.getFullIdentifier().equals(targetId) && relUnit2.getFullIdentifier().equals(sourceId));

                        if (sourceMatches) {
                                setSelectedRel(rel);
                                setHasBeenModified(true);
                                return;
                        }
                }
        }

        public void deleteStratigraphicRelationship() {
                // Check if the relationship exists in anteriorRelationships
                if (anteriorRelationships!= null) {
                        anteriorRelationships.removeIf(rel ->
                                rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                                        rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
                        );
                }

                // Check if the relationship exists in posteriorRelationships
                if (posteriorRelationships != null) {
                        posteriorRelationships.removeIf(rel ->
                                rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                                        rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
                        );
                }

                // Check if the relationship exists in synchronousRelationships
                if (synchronousRelationships != null) {
                        synchronousRelationships.removeIf(rel ->
                                rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                                        rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
                        );
                }

                if (onDelete != null) {
                        onDelete.run();
                }

                selectedRel = null;
                setHasBeenModified(true);
        }

        public void addStratigraphicRelationship() {
                FacesContext context = FacesContext.getCurrentInstance();
                UIComponent cc = UIComponent.getCurrentCompositeComponent(context);

                if (onAdd != null) {
                        onAdd.accept(context, cc);
                }

                // Optionally, reset the form fields
                conceptToAdd = null;
                targetToAdd = null;
                isUncertainToAdd = null;

                PrimeFaces.current().ajax().update(cc.getClientId().concat(":stratigraphyGraphContainer"));
        }

}
