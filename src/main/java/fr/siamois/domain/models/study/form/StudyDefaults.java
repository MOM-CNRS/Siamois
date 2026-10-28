package fr.siamois.domain.models.study.form;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The three types a new project's studies start with, and the system fields each one uses. A table's
 * fields are shared by all its types ({@link StudyForm}); a type differs only by which of them are
 * active and mandatory, which the seeder writes when the project is created.
 */
public abstract class StudyDefaults {

    private static final String LABEL_PREFIX = "study.field.";

    /**
     * A default type.
     *
     * @param key       key under {@code siamois.default-types.study.*} holding the concept URI of the type
     * @param active    bindings of the fields the type uses
     * @param mandatory bindings, among the active ones, that the type requires
     */
    public record DefaultStudyType(String key, Set<String> active, Set<String> mandatory) {

        public boolean isActive(String binding) {
            return active.contains(binding);
        }

        public boolean isMandatory(String binding) {
            return mandatory.contains(binding);
        }
    }

    /** What the studies of finds and of recording units use: both links, the method and the date. */
    private static final Set<String> LINKED_STUDY_BINDINGS =
            Set.of(StudyForm.B_RECORDING_UNITS, StudyForm.B_SPECIMENS, StudyForm.B_METHOD, StudyForm.B_STUDY_DATE);

    /** "Etude de mobilier": the study of finds. */
    public static final DefaultStudyType FIND_STUDY = new DefaultStudyType("mobilier", LINKED_STUDY_BINDINGS, Set.of());

    /** "Etude d'UE": the study of recording units. */
    public static final DefaultStudyType RECORDING_UNIT_STUDY = new DefaultStudyType("ue", LINKED_STUDY_BINDINGS, Set.of());

    /**
     * "Traitement par défaut": the columns of the "traitement" sheet of the national reference of the SDA.
     * The treated element ({@code code_element_traite}) is the linked find; the three last columns
     * ({@code nom_responsable_traitement}, {@code identifiant_lie_au_traitement},
     * {@code informations_complementaires_traitement}) are optional in the reference.
     */
    public static final DefaultStudyType TREATMENT = new DefaultStudyType("traitement",
            Set.copyOf(Stream.concat(Stream.of(StudyForm.B_SPECIMENS), StudyForm.TREATMENT_BINDINGS.stream()).toList()),
            Set.of(StudyForm.B_SPECIMENS, StudyForm.B_TREATMENT_TYPE, "reportCode", StudyForm.B_TREATMENT_START, "treatmentPlace"));

    public static final List<DefaultStudyType> TYPES = List.of(FIND_STUDY, RECORDING_UNIT_STUDY, TREATMENT);

    /** Every binding a type may use, to switch off the ones it does not. */
    public static final List<String> ALL_BINDINGS = Stream.of(
                    Stream.of(StudyForm.B_METHOD, StudyForm.B_STUDY_DATE),
                    StudyForm.LINK_BINDINGS.stream(),
                    StudyForm.TREATMENT_BINDINGS.stream())
            .flatMap(s -> s)
            .distinct()
            .toList();

    protected StudyDefaults() {}

    /** The label of a system field of the study table, from its binding. */
    public static String labelOf(String binding) {
        return LABEL_PREFIX + binding;
    }
}
