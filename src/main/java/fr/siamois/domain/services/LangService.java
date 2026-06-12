package fr.siamois.domain.services;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service to manage the language of the application
 *
 * @author Julien Linget
 */
@Slf4j
@Service
public class LangService {

    private final MessageSource messageSource;

    @Getter
    @Value("${siamois.lang.default}")
    private String defaultLang;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    @Value("${siamois.lang.available:en}")
    private String[] availableLanguages;

    public LangService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Get message from key
     * @param key key of the message
     * @return message
     */
    public String msg(String key, Locale locale) {
        return messageSource.getMessage(key, null, locale);
    }

    /**
     * Get message formatted with args. Applies {@link String#format(String, Object...)} on the message with the args.
     * @param format format of the message
     * @param args arguments to format
     * @return formatted message
     */
    public String msg(String format, Locale locale, Object... args) {
        return String.format(msg(format, locale), args);
    }

    /**
     * Locale pour les réponses OpenAPI : code langue HTTP (ex. {@code fr}, {@code en}) ou langue par défaut applicative.
     */
    public Locale localeForApiLang(@Nullable String lang) {
        if (lang == null || lang.isBlank()) {
            return Locale.forLanguageTag(defaultLang);
        }
        return Locale.forLanguageTag(lang);
    }

    /**
     * Résout une clé {@link MessageSource} ; si aucun message n'existe pour ce code, renvoie la chaîne d'origine
     * (libellé libre, valeur déjà affichable, ou clé inconnue).
     */
    public String resolveMessage(@Nullable String code, Locale locale) {
        if (code == null || code.isBlank()) {
            return code;
        }
        Locale loc = locale != null ? locale : Locale.forLanguageTag(defaultLang);
        return messageSource.getMessage(code, null, code, loc);
    }

}
