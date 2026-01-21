package fr.siamois.domain.models.actionunit;

/**
 * This enum defines the type of resolution specified by the codes in the Action Unit Format
 * Specs are in the User Story 90
 * <ul>
 *     <li>UNIQUE = NUM_UE</li>
 *     <li>PARENT = NUM_UE, NUM_PARENT</li>
 *     <li>TYPE_UNIQUE = NUM_UE, TYPE_UE</li>
 *     <li>PARENT_TYPE = NUM_UE, NUM_PARENT, TYPE_UE</li>
 *     <li>NONE if the format is invalid</li>
 * </ul>
 */
public enum ActionUnitResolveConfig {
    UNIQUE,
    PARENT,
    TYPE_UNIQUE,
    PARENT_TYPE,
    NONE
}
