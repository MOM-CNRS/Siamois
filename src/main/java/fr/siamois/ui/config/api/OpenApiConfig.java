package fr.siamois.ui.config.api;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SIAMOIS — API v0 (peut être modifier d'ici la v1)",
                version = "1.0",
                description = """
                        API REST SIAMOIS

                        **Authentification** — `POST /api/v1/auth/login` (public) renvoie un JWT. \
                        Toutes les autres routes `/api/v1/**` exigent l'en-tête `Authorization: Bearer <token>`. \
                        À l'expiration du jeton, rappeler login (pas de refresh token).

                        **Pagination** — paramètres `offset` (défaut 0) et `limit` (souvent 20, max 200 selon l'endpoint). \
                        Le total est exposé dans l'en-tête de réponse `X-Total-Count` et dans `meta.total` lorsque présent.

                        **Langue** — en-tête `Accept-Language` (ex. `fr`, `en`) pour les libellés de champs et de vocabulaires.

                        **Clés de ressources**
                        - *Projet* (`action_unit`) : id numérique, `fullIdentifier`, ou identifiant court dans une organisation accessible.
                        - *Unité d'enregistrement* : `recording_unit_id` numérique ou `full_identifier`.
                        - *Mobilier* (GET) : `specimen_id` (chaîne numérique) ou `full_identifier` ; PATCH : `specimen_id` numérique uniquement.

                        **Formulaires dynamiques** — `fieldAnswers` sur POST/PATCH : clés = identifiants `custom_field_id` (chaînes). \
                        Pour les listes contrôlées, envoyer `concept.id` (pas `externalId`). \
                        Vocabulaires par organisation : `GET /api/v1/vocabularies?organizationId=…`. \
                        Types d'UE : concepts du field_code `SIARU.TYPE` dans ce vocabulaire ; \
                        formulaire de création UE : `GET /api/v1/recording-units/creation-form?organizationId=…&recordingUnitTypeConceptId=…`.
                        """,
                contact = @Contact(name = "SIAMOIS")
        ),
        tags = {
                @Tag(name = OpenApiTags.AUTH, description = "Connexion JWT et profil utilisateur."),
                @Tag(name = OpenApiTags.ORGANISATION, description = "Institutions accessibles dans le périmètre du jeton."),
                @Tag(name = OpenApiTags.PROJECT, description = "Projets : liste, fiche, formulaire, documents, UE rattachées."),
                @Tag(name = OpenApiTags.RECORDING_UNIT, description = "Unités d'enregistrement : création, détail, relations, mobiliers, documents."),
                @Tag(name = OpenApiTags.FIND, description = "Mobilier : formulaire, création et mise à jour."),
                @Tag(name = OpenApiTags.DOCUMENT, description = "Fichiers et formulaires documentaires."),
                @Tag(name = OpenApiTags.SPATIAL_UNIT, description = "Recherche de lieux pour l'autocomplétion (contexte spatial des projets)."),
                @Tag(name = OpenApiTags.USER, description = "Utilisateurs rattachés à une organisation (équipes, gestionnaires).")
        },
        security = @SecurityRequirement(name = "bearer-jwt")
)
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT (header `Authorization: Bearer …`). Obtenu via POST /api/v1/auth/login."
)
public class OpenApiConfig {
}
