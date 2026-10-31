package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.services.dataimport.ImportAsyncRunner;
import fr.siamois.domain.services.dataimport.ImportVocabularyLinkService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.initializer.seeder.ProjectDataSeeder;
import fr.siamois.infrastructure.dataimport.ImportError;
import fr.siamois.infrastructure.dataimport.OOXMLImportService;
import fr.siamois.infrastructure.dataimport.VocabularyResolutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectUploadSettingsBeanVocabularyLinksTest {

    @Mock private OOXMLImportService importService;
    @Mock private ProjectDataSeeder seeder;
    @Mock private ImportAsyncRunner asyncRunner;
    @Mock private ImportVocabularyLinkService linkService;

    private ProjectUploadSettingsBean bean;
    private ActionUnitDTO project;

    @BeforeEach
    void setUp() {
        bean = new ProjectUploadSettingsBean(importService, seeder, asyncRunner, linkService);
        project = new ActionUnitDTO();
        project.setId(5L);
        bean.setProject(project);
    }

    private static ImportError vocabularyError(int row) {
        return ImportError.forRow("UE", row, new VocabularyResolutionException("SIARU.NATURE", "introuvable", null), "US");
    }

    @Test
    void getVocabularyLinks_anErrorOutsideTheThesaurus_hasNoLink() {
        var links = bean.getVocabularyLinks(new ImportError("UE", 3, "x", "date invalide"));

        assertThat(links.hasAny()).isFalse();
        verifyNoInteractions(linkService);
    }

    @Test
    void getVocabularyLinks_noError_hasNoLink() {
        assertThat(bean.getVocabularyLinks(null).hasAny()).isFalse();
    }

    @Test
    void getVocabularyLinks_asksTheServiceOnceForTheSameFieldAndType() {
        var expected = new ImportVocabularyLinkService.VocabularyLinks("/c", ImportVocabularyLinkService.ConfigKind.FIELD, "US", "/t", "th338");
        when(linkService.linksFor(eq(project), any(ImportError.class), eq(""))).thenReturn(expected);

        var first = bean.getVocabularyLinks(vocabularyError(4));
        var second = bean.getVocabularyLinks(vocabularyError(4));

        assertThat(first).isSameAs(expected);
        assertThat(second).isSameAs(expected);
        verify(linkService, times(1)).linksFor(eq(project), any(ImportError.class), eq(""));
    }
}
