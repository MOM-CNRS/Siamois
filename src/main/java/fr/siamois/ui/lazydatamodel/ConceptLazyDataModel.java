package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.settings.ConceptFieldConfig;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import lombok.RequiredArgsConstructor;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ConceptLazyDataModel extends LazyDataModel<Concept> {

    private final LabelService labelService;
    private final FieldConfigurationService fieldConfigurationService;
    private UserInfo userInfo;
    private ConceptFieldConfig config;

    private int resultCount = 0;

    public void prepare(UserInfo info, String fieldCode) throws NoConfigForFieldException {
        this.userInfo = info;
        config = fieldConfigurationService.findConfigurationForFieldCode(info, fieldCode);
    }

    @Override
    public int count(Map<String, FilterMeta> map) {
        return resultCount;
    }

    @Override
    public List<Concept> load(int first, int pageSize, Map<String, SortMeta> sortBy, Map<String, FilterMeta> filterBy) {
        Pageable pageable = PageRequest.of(first, pageSize);
        List<Concept> results = labelService.findAllCandidatesConcept(config.getConcept(), userInfo.getLang(), pageable);
        resultCount = results.size();
        return results;
    }

}
