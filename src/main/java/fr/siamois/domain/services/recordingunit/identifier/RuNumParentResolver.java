package fr.siamois.domain.services.recordingunit.identifier;

import fr.siamois.domain.models.recordingunit.identifier.RecordingUnitIdInfo;
import fr.siamois.domain.services.recordingunit.identifier.generic.RuNumericalIdentifierResolver;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitIdInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RuNumParentResolver extends RuNumericalIdentifierResolver {

    private final RecordingUnitIdInfoRepository recordingUnitIdInfoRepository;

    public RuNumParentResolver(RecordingUnitIdInfoRepository recordingUnitIdInfoRepository) {
        super("NUM_PARENT", "ru.identifier.description.number_parent", "ru.identifier.title.number_parent");
        this.recordingUnitIdInfoRepository = recordingUnitIdInfoRepository;
    }

    @Override
    protected int numericalValue(@NonNull RecordingUnitIdInfo info) {
        if (info.getParent() == null) {
            return 0;
        }
        Optional<RecordingUnitIdInfo> opt = recordingUnitIdInfoRepository.findById(info.getParent().getId());
        return opt.map(RecordingUnitIdInfo::getRuNumber).orElse(0);
    }

    @NonNull
    @Override
    public String getButtonStyleClass() {
        return "rounded-button ui-button-warning";
    }
}
