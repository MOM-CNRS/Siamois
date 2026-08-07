package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.identifier.IdentifierResolver;
import fr.siamois.domain.services.identifier.IdentifierResolverRegistry;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.ConceptCollectionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import fr.siamois.ui.bean.LangBean;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTableFieldSettingsBeanTest {

    @Test
    void identifierTab_shouldLoadSelectedTypeConfigAndUseItsTableCatalog() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        TypeFormConfig config = TypeFormConfig.builder()
                .typeName("Ceramique")
                .identifierFormat("M-{NUM_MOBILIER:000}-{ID_UE}")
                .minCode(0)
                .maxCode(999)
                .build();
        when(tableService.getFormConfig(42L, ConfigurableTable.MOBILIER, "Ceramique")).thenReturn(config);

        ProjectTableFieldSettingsBean bean = bean(tableService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.MOBILIER);
        bean.selectType("Ceramique");

        assertThat(bean.isIdentTabAvailable()).isTrue();
        assertThat(bean.isIdentifierGenerationAvailable()).isFalse();
        assertThat(bean.getIdentFirst()).isZero();
        assertThat(bean.getIdentLast()).isEqualTo(999);
        assertThat(bean.getIdentifierResolvers()).extracting(IdentifierResolver::code)
                .containsExactly("NUM_MOBILIER", "NUM_PARENT", "ID_PARENT", "NUM_UE", "ID_UE", "ID_UA");
        assertThat(bean.getIdentExample()).isEqualTo("M-027-XXX");
    }

    @Test
    void identifierTab_shouldAlsoBeAvailableForDefaultUeConfig() {
        TableFieldConfigService tableService = mock(TableFieldConfigService.class);
        when(tableService.getFormConfig(42L, ConfigurableTable.UE, "_default"))
                .thenReturn(TypeFormConfig.builder()
                        .typeName("_default")
                        .identifierFormat("{NUM_UE:000}-{NUM_PARENT:00}-{ID_PARENT}-{NUM_USPATIAL:0000}")
                        .minCode(0)
                        .maxCode(999)
                        .build());

        ProjectTableFieldSettingsBean bean = bean(tableService);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(42L);
        bean.setProject(project);
        bean.setSelectedTable(ConfigurableTable.UE);
        bean.selectType("_default");

        assertThat(bean.isIdentifierGenerationAvailable()).isTrue();
        assertThat(bean.getIdentExample()).isEqualTo("142-00-XXX-0000");
    }

    private ProjectTableFieldSettingsBean bean(TableFieldConfigService tableService) {
        LangBean langBean = mock(LangBean.class);
        when(langBean.msg(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ProjectTableFieldSettingsBean(
                tableService,
                mock(FormConfigService.class),
                mock(ConceptService.class),
                mock(ConceptCollectionService.class),
                mock(VocabularyService.class),
                mock(VocabularyMapper.class),
                mock(LabelService.class),
                langBean,
                new IdentifierResolverRegistry());
    }
}
