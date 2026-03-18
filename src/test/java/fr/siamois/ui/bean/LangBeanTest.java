package fr.siamois.ui.bean;

import fr.siamois.domain.services.LangService;
import fr.siamois.domain.services.person.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LangBeanTest {

    private LangBean langBean;

    @Mock
    private LangService langService;

    @Mock
    private PersonService personService;

    @Mock
    private ConversionService conversionService;

    @BeforeEach
    void setUp() {
        langBean = new LangBean(langService, personService, conversionService);
    }

    @Test
    void getLangs_shouldReturnAllLangsWithQuotesWithQuote() {
        when(langService.getAvailableLanguages()).thenReturn(new String[]{"fr", "en", "de"});

        List<String> result = langBean.getLangsWithQuotes();

        assertThat(result).containsExactlyInAnyOrder("'fr'", "'en'", "'de'");
    }

}