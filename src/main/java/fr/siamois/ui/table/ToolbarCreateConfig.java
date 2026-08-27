package fr.siamois.ui.table;

import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.function.BooleanSupplier;
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
    private final transient Supplier<NewUnitContext.Scope> scopeSupplier = NewUnitContext.Scope::none;

    /**
     * Insert policy toolbar (optionnel).
     * Si null: on utilisera (list=TOP, tree=ROOT).
     */
    private transient Supplier<NewUnitContext.UiInsertPolicy> insertPolicySupplier;

    /**
     * Permet de surcharger le trigger toolbar si un jour tu veux.
     * Sinon toolbar par défaut.
     */
    @Builder.Default
    private final transient Supplier<NewUnitContext.Trigger> triggerSupplier = NewUnitContext.Trigger::toolbar;

    /**
     * Whether the current user is allowed to create this entity from this toolbar (optional).
     * Defaults to true when the target scope isn't known yet at click time (e.g. a home-page-level
     * list where the target project is picked later in the dialog) — the real gate is then the
     * server-side permission check performed at save time.
     */
    @Builder.Default
    private final transient BooleanSupplier createAllowedSupplier = () -> true;

    /**
     * Message key explaining why creation isn't allowed here (optional). When {@code
     * createAllowedSupplier} is false and this returns a non-null key, the toolbar shows this
     * message plus a link (see {@link #unavailableLinkAction}) in the button's place instead of
     * leaving the toolbar slot blank.
     */
    @Builder.Default
    private final transient Supplier<String> unavailableMessageKeySupplier = () -> null;

    /** Message key for the "unavailable" message's link label (optional, e.g. "a project"). */
    @Builder.Default
    private final transient Supplier<String> unavailableLinkLabelKeySupplier = () -> null;

    /** Action run when the "unavailable" message's link is clicked (optional). */
    @Builder.Default
    private final transient Runnable unavailableLinkAction = () -> {};
}

