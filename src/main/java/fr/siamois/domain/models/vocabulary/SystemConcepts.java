package fr.siamois.domain.models.vocabulary;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Identifiants des concepts des champs système dans le thésaurus système (th337, « Socle commun »).
 * <p>
 * Une clé s'écrit {@code TABLE.propriété} (champ simple, ex. {@code SIAAU.name}) ou {@code TABLE.CODE}
 * (champ à vocabulaire, ex. {@code SIARU.TYPE}) ; la table de correspondance est
 * {@code thesaurus-system-concepts.properties}, générée depuis le thésaurus. Les anciens identifiants
 * du th230 y figurent aussi, pour relire les modèles d'export enregistrés avant la migration.
 */
public final class SystemConcepts {

    /** Ancien thésaurus système. */
    public static final String LEGACY_THESAURUS = "th230";
    /** Thésaurus système actuel. */
    public static final String THESAURUS = "th337";

    private static final String LEGACY_PREFIX = "legacy.";
    private static final Properties CONCEPTS = load();

    private SystemConcepts() {
        throw new UnsupportedOperationException();
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = SystemConcepts.class.getClassLoader().getResourceAsStream("thesaurus-system-concepts.properties")) {
            if (in == null) {
                throw new IllegalStateException("thesaurus-system-concepts.properties is missing");
            }
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /** Identifiant du concept d'un champ système ; erreur si la clé n'existe pas dans le thésaurus. */
    public static String id(String key) {
        String id = CONCEPTS.getProperty(key);
        if (id == null) {
            throw new IllegalStateException("No system concept for " + key);
        }
        return id;
    }

    /**
     * Identifiant du champ {@code binding} de {@code table} : celui que la table de correspondance lui donne (par son
     * ancien identifiant symbolique {@code legacy}, qui porte les alias comme {@code title} → {@code SIAD.name}, ou par
     * {@code table.binding}), sinon {@code legacy} lui-même pour un champ système que le thésaurus ne décrit pas encore.
     */
    public static String id(String table, String binding, String legacy) {
        String id = upgrade(table, legacy);
        if (id == null) {
            id = CONCEPTS.getProperty(table + "." + binding);
        }
        return id == null ? legacy : id;
    }

    /**
     * Identifiant actuel d'un concept système désigné par son ancien identifiant (th230) pour une table, ou
     * {@code null} si cet ancien identifiant est inconnu.
     */
    public static String upgrade(String table, String legacyId) {
        return CONCEPTS.getProperty(LEGACY_PREFIX + table + "." + legacyId);
    }
}
