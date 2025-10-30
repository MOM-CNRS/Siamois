package fr.siamois.domain.models.recordingunit;

import jakarta.persistence.Embeddable;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;

@Embeddable
@Data
@Audited
public class RecordingUnitSize implements Serializable {

    private BigDecimal sizeLength;
    private BigDecimal sizeWidth;
    private BigDecimal sizeThickness;
    private String sizeUnit;

}
