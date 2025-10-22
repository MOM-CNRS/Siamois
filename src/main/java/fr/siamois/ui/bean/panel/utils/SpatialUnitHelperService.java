package fr.siamois.ui.bean.panel.utils;

import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.history.HistoryAuditService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpatialUnitHelperService {

    private final SpatialUnitService spatialUnitService;
    private final HistoryAuditService historyAuditService;


    public void restore(RevisionWithInfo<SpatialUnit> history) {
        log.trace("Restore order received");
        spatialUnitService.restore(history);

    }



    public <T> List<T> getFirstThree(Set<T> itemSet) {
        if(itemSet == null) {
            return new ArrayList<>();
        }
        else if (itemSet.size() <= 3) {
            return new ArrayList<>(itemSet);
        } else {
            List<T> tempList = new ArrayList<>(itemSet);
            return tempList.subList(0, 3);
        }
    }

    public List<RevisionWithInfo<SpatialUnit>> findHistory(SpatialUnit spatialUnit) {
        return historyAuditService.findAllRevisionForEntity(SpatialUnit.class, spatialUnit.getId());
    }

    public void reinitialize(Consumer<SpatialUnit> spatialUnitSetter,
                             Consumer<String> spatialUnitErrorMessageSetter,
                             Consumer<String> spatialUnitListErrorMessageSetter,
                             Consumer<List<SpatialUnit>> spatialUnitListSetter,
                             Consumer<List<SpatialUnit>> spatialUnitParentsListSetter,
                             Consumer<String> spatialUnitParentsListErrorMessageSetter) {

        spatialUnitSetter.accept(null);
        spatialUnitErrorMessageSetter.accept(null);
        spatialUnitListErrorMessageSetter.accept(null);
        spatialUnitListSetter.accept(null);
        spatialUnitParentsListSetter.accept(null);
        spatialUnitParentsListErrorMessageSetter.accept(null);
    }

}

