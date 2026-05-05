package fr.siamois.ui.form.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CustomRowUiDto implements Serializable {

    private List<CustomColUiDto> columns;

}
