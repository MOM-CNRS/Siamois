package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.form.customform.CustomRow;
import lombok.Data;
import org.jboss.weld.util.LazyValueHolder;

import java.io.Serializable;
import java.util.List;


public record CustomFormPanelDTO (
    String className,
    String name,
    List<CustomRowDTO> rows,
    Boolean isSystemPanel // define by system or user?
) implements Serializable {}
