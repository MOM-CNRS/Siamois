package fr.siamois.utils;

import org.springframework.lang.Nullable;

/**
 * Génère le script client qui déplace le surlignage "fiche ouverte" (classe {@code overview-open},
 * cadre vert) vers la ligne {@code entity-row-<id>} d'une table, sans re-rendre la table côté
 * serveur (re-rendre 25+ lignes × moteur de formulaire coûtait ~700 ms par clic).
 *
 * <p>Le script est volontairement autonome (IIFE + try/catch, aucune fonction de template.js) :
 * il ne peut ni dépendre d'un fichier JS potentiellement en cache navigateur, ni interrompre les
 * autres scripts du même bloc {@code <eval>} PrimeFaces (ex. {@code showSideview}).</p>
 *
 * <p>Les lignes portent la classe stable {@code entity-row-<id>} via
 * {@code EntityTableViewModel.getRowStyleClass} ; le {@code overviewEntityId} posé côté serveur
 * garde les vrais re-rendus de la table (tri, filtre, pagination) cohérents.</p>
 */
public final class OverviewHighlightJs {

    private OverviewHighlightJs() {
    }

    /**
     * @param scopeClientId id client DOM dans lequel chercher les lignes (table ou conteneur de
     *                      panneau) ; si absent du DOM, la recherche se fait dans tout le document.
     * @param entityId      id de l'entité à surligner ; {@code null} pour seulement retirer le
     *                      surlignage (fermeture de l'aperçu).
     * @return un statement JS terminé par {@code ;}, sûr à concaténer avec d'autres scripts.
     */
    public static String moveHighlightScript(String scopeClientId, @Nullable Long entityId) {
        String idPart = entityId == null ? "" : String.valueOf(entityId);
        return "(function(){try{"
                + "var c=document.getElementById('" + scopeClientId + "');"
                + "var s=c?$(c):$(document);"
                + "s.find('tr.overview-open').removeClass('overview-open');"
                + (idPart.isEmpty() ? "" : "s.find('tr.entity-row-" + idPart + "').addClass('overview-open');")
                + "}catch(e){}})();";
    }
}
