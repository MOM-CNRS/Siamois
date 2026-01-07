package fr.siamois.ui.bean.dialog.newunit.handler;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.EntityAlreadyExistsException;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.exceptions.CannotInitializeNewUnitDialogException;

import java.util.List;


public interface INewUnitHandler<T extends TraceableEntity> {
    UnitKind kind();
    T newEmpty();
    T save(UserInfo user, T unit) throws EntityAlreadyExistsException;
    String dialogWidgetVar();
    void initFromContext(GenericNewUnitDialogBean<?> bean) throws CannotInitializeNewUnitDialogException; // optionnel pour pré-remplir (parents/enfants…)
    String getName(T unit); // Get unit name
    List<SpatialUnit> getSpatialUnitOptions(T unit);

    // shared UI defaults (pull from UnitKind)
    default String getResourceUri()     { return kind().getConfig().resourceUri(); }
    default String getTitle()           { return kind().getConfig().title(); }
    default String getTitle(NewUnitContext ctx) {
        return kind().getConfig().title();
    }
    default String styleClassName()     { return kind().getConfig().styleClass(); }
    default String getIcon()            { return kind().getConfig().icon(); }
    default String getAutocompleteClass(){ return kind().getConfig().autocompleteClass(); }
    default CustomForm formLayout(){ return kind().getConfig().customForm(); }
    default String viewUrlFor(Long id){ return kind().getConfig().urlPrefix() + id; }
    default String successMessageCode(){ return kind().getConfig().successMessageCode(); }

}

