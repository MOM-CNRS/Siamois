package fr.siamois.infrastructure.database.projection;


public interface SpecimenProjection {

    Integer getIdentifier();

    String getFullIdentifier();

    Boolean getValidated();

}