package fr.siamois.ui.lazydatamodel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecordingUnitScope {

    public enum Type {
        RU_IN_INSTITUTION,
        ACTION
    }

    private final Type type;

    private final Long institutionId;

    // only used when type == ACTION
    private final Long actionId;

}
