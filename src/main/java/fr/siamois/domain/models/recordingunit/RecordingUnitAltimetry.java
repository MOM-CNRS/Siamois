package fr.siamois.domain.models.recordingunit;

import jakarta.persistence.Embeddable;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;

@Embeddable
@Data
@Audited
public class RecordingUnitAltimetry implements Serializable {

    private BigDecimal altitudeSupPlus;
    private BigDecimal altitudeSupMinus;
    private BigDecimal altitudeInfPlus;
    private BigDecimal altitudeInfMinus;
    private String altitudeUnit;

}
