package fr.siamois.ui.bean.settings;

import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.services.vocabulary.VocabularyService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the pickers of an OpenTheso instance and of one of its thesauri have in common: the instances to offer, and the
 * suggestions matching what was typed.
 */
public final class ThesaurusServers {

    private ThesaurusServers() {
    }

    /** An instance address without its trailing slash. */
    public static String normalize(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** The instances to pick from: the default one, then those of the thesauri the database knows. */
    public static List<String> known(VocabularyService vocabularyService, String defaultServer) {
        Set<String> servers = new LinkedHashSet<>();
        servers.add(defaultServer);
        vocabularyService.findAll().forEach(v -> servers.add(normalize(v.getBaseUri())));
        return new ArrayList<>(servers);
    }

    /** The instances containing what was typed. */
    public static List<String> matching(List<String> servers, String query) {
        String needle = needle(query);
        return servers.stream().filter(server -> server.toLowerCase().contains(needle)).toList();
    }

    /** The thesauri whose label or identifier contains what was typed. */
    public static List<ThesaurusInfo> matchingThesauri(List<ThesaurusInfo> thesauri, String query) {
        String needle = needle(query);
        return thesauri.stream()
                .filter(info -> info.label().toLowerCase().contains(needle) || info.idTheso().toLowerCase().contains(needle))
                .toList();
    }

    private static String needle(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }
}
