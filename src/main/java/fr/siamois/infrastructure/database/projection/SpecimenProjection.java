package fr.siamois.infrastructure.database.projection;


import fr.siamois.domain.models.ValidationStatus;

public interface SpecimenProjection {

    Integer getIdentifier();

    String getFullIdentifier();

    ValidationStatus getValidated();

}