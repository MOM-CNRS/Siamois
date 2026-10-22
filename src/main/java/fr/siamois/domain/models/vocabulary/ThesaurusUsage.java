package fr.siamois.domain.models.vocabulary;

import lombok.Value;

/**
 * One place where a project or an organisation draws a list from a thesaurus: a branch or a
 * collection, for a field, optionally for one entity type.
 *
 * A class rather than a record: the EL of the JSF pages (EL 5) only reads JavaBean getters.
 *
 * @param thesaurusLabel label of the thesaurus the source belongs to
 * @param kind           branch or collection
 * @param sourceLabel    label of the branch's top term, or external id of the collection
 * @param fieldLabel     the field the list is used in
 * @param typeLabel      the entity type the configuration is specific to, or null for the default one
 * @param frozen         whether the list is frozen rather than following its source
 */
@Value
public class ThesaurusUsage {
    String thesaurusLabel;
    FieldVocabularyConfig.Kind kind;
    String sourceLabel;
    String fieldLabel;
    String typeLabel;
    boolean frozen;
}
