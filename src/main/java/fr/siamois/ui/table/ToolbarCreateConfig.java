package fr.siamois.ui.table;

import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.function.Supplier;

@Getter
@Builder
public class ToolbarCreateConfig implements Serializable {

    /** Entité à créer quand on clique sur le bouton du haut */
    private final UnitKind kindToCreate;

    /**
     * Scope imposé par la table/écran (optionnel)
     * ex: linkedTo("ACTION", actionId)
     */
    @Builder.Default
    private final Supplier<NewUnitContext.Scope> scopeSupplier = NewUnitContext.Scope::none;

    /**
     * Insert policy toolbar (optionnel).
     * Si null: on utilisera (list=TOP, tree=ROOT).
     */
    private final Supplier<NewUnitContext.UiInsertPolicy> insertPolicySupplier;

    /**
     * Permet de surcharger le trigger toolbar si un jour tu veux.
     * Sinon toolbar par défaut.
     */
    @Builder.Default
    private final Supplier<NewUnitContext.Trigger> triggerSupplier = NewUnitContext.Trigger::toolbar;
}

