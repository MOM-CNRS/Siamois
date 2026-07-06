package fr.siamois.domain.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LangServiceTest {

    @Mock
    private MessageSource messageSource;

    private LangService langService;

    @BeforeEach
    void setUp() {
        langService = new LangService(messageSource);
        ReflectionTestUtils.setField(langService, "defaultLang", "fr");
    }

    @Test
    void msg_returnsMessageFromSource() {
        when(messageSource.getMessage("greeting", null, Locale.ENGLISH)).thenReturn("Hello");

        assertThat(langService.msg("greeting", Locale.ENGLISH)).isEqualTo("Hello");
    }

    @Test
    void msg_withArgs_formatsMessage() {
        when(messageSource.getMessage("greeting", null, Locale.ENGLISH)).thenReturn("Hello, %s!");

        assertThat(langService.msg("greeting", Locale.ENGLISH, "John")).isEqualTo("Hello, John!");
    }

    @Test
    void getAvailableLanguages_returnsConfiguredValues() {
        langService.setAvailableLanguages(new String[]{"en", "fr", "de"});

        assertThat(langService.getAvailableLanguages()).containsExactly("en", "fr", "de");
    }

    @Test
    void getDefaultLang_returnsConfiguredDefault() {
        assertThat(langService.getDefaultLang()).isEqualTo("fr");
    }

    @Test
    void localeForApiLang_nullOrBlank_usesDefaultLang() {
        assertThat(langService.localeForApiLang(null)).isEqualTo(Locale.forLanguageTag("fr"));
        assertThat(langService.localeForApiLang("  ")).isEqualTo(Locale.forLanguageTag("fr"));
    }

    @Test
    void localeForApiLang_explicitLang_usesLanguageTag() {
        assertThat(langService.localeForApiLang("en")).isEqualTo(Locale.forLanguageTag("en"));
        assertThat(langService.localeForApiLang("de")).isEqualTo(Locale.forLanguageTag("de"));
    }

    @Test
    void resolveMessage_nullOrBlankCode_returnsAsIs() {
        assertThat(langService.resolveMessage(null, Locale.ENGLISH)).isNull();
        assertThat(langService.resolveMessage("  ", Locale.ENGLISH)).isEqualTo("  ");
    }

    @Test
    void resolveMessage_nullLocale_usesDefaultLang() {
        when(messageSource.getMessage(eq("key"), isNull(), eq("key"), eq(Locale.forLanguageTag("fr"))))
                .thenReturn("traduit");

        assertThat(langService.resolveMessage("key", null)).isEqualTo("traduit");
    }

    @Test
    void resolveMessage_withLocale_delegatesToMessageSourceWithFallbackCode() {
        when(messageSource.getMessage("label.code", null, "label.code", Locale.ENGLISH))
                .thenReturn("Label");

        assertThat(langService.resolveMessage("label.code", Locale.ENGLISH)).isEqualTo("Label");
        verify(messageSource).getMessage("label.code", null, "label.code", Locale.ENGLISH);
    }

    @Test
    void resolveMessage_unknownKey_returnsOriginalCodeAsFallback() {
        when(messageSource.getMessage(eq("unknown"), isNull(), eq("unknown"), same(Locale.FRENCH)))
                .thenReturn("unknown");

        assertThat(langService.resolveMessage("unknown", Locale.FRENCH)).isEqualTo("unknown");
    }
}
