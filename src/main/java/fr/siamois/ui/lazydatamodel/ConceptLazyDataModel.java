package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.vocabulary.Concept;
import lombok.RequiredArgsConstructor;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.faces.bean.SessionScoped;
import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ConceptLazyDataModel extends LazyDataModel<Concept> {

    private UserInfo userInfo;
    private String fieldCode;

    public void prepare(UserInfo info, String fieldCode) {
        this.userInfo = info;
        this.fieldCode = fieldCode;
    }

    @Override
    public int count(Map<String, FilterMeta> map) {
        return 0;
    }

    @Override
    public List<Concept> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        return List.of();
    }

}
