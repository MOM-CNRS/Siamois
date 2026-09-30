package fr.siamois.utils;

import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.form.layout.FormLayoutComposer;
import fr.siamois.domain.services.form.layout.FormLayoutSeeds;
import fr.siamois.ui.form.dto.FormUiDto;

import java.util.Optional;

/**
 * The initial form of a table, composed from its layout seed without any database: what a project
 * that configured nothing gets. Every concept a seed cites resolves to {@link #CONCEPT_ID}.
 */
public final class TestForms {

    public static final long CONCEPT_ID = 77L;

    private static final FormLayoutSeeds SEEDS = new FormLayoutSeeds((vocabulary, concept) -> Optional.of(CONCEPT_ID));

    private TestForms() {
        throw new UnsupportedOperationException();
    }

    public static FormLayoutSeeds seeds() {
        return SEEDS;
    }

    public static FormLayout layoutOf(ConfigurableTable table) {
        return SEEDS.layoutOf(table);
    }

    public static FormUiDto of(ConfigurableTable table) {
        return FormLayoutComposer.compose(SEEDS.layoutOf(table), table);
    }
}
