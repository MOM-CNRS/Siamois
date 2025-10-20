package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.services.attributeconverter.CustomFormLayoutConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


public record CustomFormDTO (
     String description,
     String name,
     List<CustomFormPanelDTO> layout
) implements Serializable {}
