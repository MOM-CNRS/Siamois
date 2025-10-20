package fr.siamois.infrastructure.database.initializer.seeder.customform;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomRow;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

public record CustomRowDTO (List<CustomColDTO> columns) implements Serializable {}

