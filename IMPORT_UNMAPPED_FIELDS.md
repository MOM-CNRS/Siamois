# Champs non mappés dans l'importeur, par feuille

Ce document liste, pour chaque feuille du format d'import (voir `IMPORT_FORMAT.md`), les champs des
entités JPA réelles qui ne sont **pas** lus par l'importeur (`OOXMLImportService.java`) aujourd'hui.

**Note de portée** : Institution, Personne, Code et Unité d'action ne sont importés qu'en import
global (`ImportScope.ALL`), jamais en import dans un projet.

Pour chaque feuille : un tableau des champs persistés de l'entité, puis une synthèse en trois
catégories :
- **Vrais manques** : données métier sans aucun chemin d'import.
- **Champs système/workflow exclus intentionnellement** : statut de validation, ARK, audit, mots de passe, etc.
- **Relations gérées par une autre feuille** : pas un manque, juste alimenté ailleurs.

---

## Institution

Entité : `Institution.java`. Spec : `InstitutionSeeder.InstitutionSpec`. Colonnes attendues : `nom`, `description`, `identifiant`, `email admins`, `thesaurus`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `name` | `institution_name` | Oui | `nom` |
| `description` | `institution_description` | Oui | `description` |
| `identifier` | `identifier` | Oui | `identifiant` |
| `creationDate` | `creation_date` | Non | — |

**Vrais manques** : aucun.
**Système exclu** : `creationDate` (auto `now()`).
**Relations ailleurs** : `managerEmails` ("email admins") et le thésaurus ne sont pas des colonnes directes d'`Institution` — ils alimentent des tables/relations séparées (gestionnaires, configuration de champs).

---

## Personne

Entité : `Person.java`. Spec : `PersonSeeder.PersonSpec`. Colonnes attendues : `email`, `nom`, `prenom`, `identifiant`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `name` | `name` | Oui | `nom` |
| `lastname` | `lastname` | Oui | `prenom` |
| `username` | `username` | Oui | `identifiant` |
| `email` | `mail` | Oui | `email` |
| `alertMail` | `alert_mail` | Non | — |
| `password` | `password` | Non (placeholder codé en dur) | — |
| `passToModify` | `pass_to_modify` | Non | — |
| `apiKey` | `api_key` | Non | — |
| `keyDescription` | `key_description` | Non | — |
| `keyNeverExpire` | `key_never_expire` | Non | — |
| `keyExpiresAt` | `key_expires_at` | Non | — |
| `isServiceAccount` | `is_service_account` | Non | — |
| `isExpired` | `is_expired` | Non | — |
| `isLocked` | `is_locked` | Non | — |
| `isEnabled` | `is_enabled` | Non | — |

**Vrais manques** : `alertMail` (préférence utilisateur, mineure).
**Système/sécurité exclus** : `password`, `apiKey`, `keyDescription`, `keyNeverExpire`, `keyExpiresAt`, `isServiceAccount`, `isExpired`, `isLocked`, `isEnabled`, `passToModify`.
**Note** : la colonne `nom` alimente `Person.name` et `prenom` alimente `Person.lastname` — l'intitulé français peut prêter à confusion vis-à-vis du nom des champs.

---

## Code (ActionCode)

Entité : `ActionCode.java`. Spec : `ActionCodeSeeder.ActionCodeSpec`. Colonnes attendues : `code`, `type uri`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `code` (clé) | `action_code_id` | Oui | `code` |
| `type` | `fk_type` | Oui | `type uri` |

**Vrais manques** : aucun — entité minimale, entièrement couverte.

---

## Unité d'action (ActionUnit)

Entité : `ActionUnit.java` (+ `TraceableEntity`). Spec : `ActionUnitSeeder.ActionUnitSpecs`. Colonnes attendues : `nom`, `identifiant`, `code`, `type uri`, `createur`, `institution`, `contexte spatiale`, `date debut`, `date fin`, `localisation principale`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `name` | `name` | Oui | `nom` |
| `identifier` | `identifier` | Oui | `identifiant` |
| `fullIdentifier` | `full_identifier` | Oui (dérivé) | `identifiant`/`nom` |
| `type` | `fk_type` | Oui | `type uri` |
| `primaryActionCode` | `fk_primary_action_code` | Oui | `code` |
| `beginDate` | `begin_date` | Oui | `date debut` |
| `endDate` | `end_date` | Oui | `date fin` |
| `mainLocation` | `fk_main_location` | Oui | `localisation principale` |
| `spatialContext` | `action_unit_spatial_context` | Oui | `contexte spatiale` (séparateur `&&`) |
| `createdBy` (TraceableEntity) | `fk_created_by` | Oui | `createur` (email) |
| `createdByInstitution` (TraceableEntity) | `fk_institution_id` | Oui | `institution` |
| `secondaryActionCodes` | `action_action_code` | Non | — |
| `children`/`parents` (hiérarchie action unit) | `action_hierarchy` | Non | — |
| `documents` | `action_unit_document` | Non | — |
| `recordingUnitIdentifierFormat` | `recording_unit_identifier_format` | Non (défaut `"{NUM_UE}"`) | — |
| `recordingUnitIdentifierLang` | `recording_unit_identifier_lang` | Non | — |
| `minRecordingUnitCode` / `maxRecordingUnitCode` | `min_recording_unit_code` / `max_recording_unit_code` | Non (défauts codés en dur) | — |
| `hasChildrens` | `has_childrens` | Non (maintenu par trigger DB) | — |
| `ark` | `fk_ark_id` | Non | — |
| `formsAvailable` | — | Non | — |
| `creationTime`, `validated`, `validatedAt`, `validatedBy` (TraceableEntity) | — | Non | — |

**Vrais manques** : `secondaryActionCodes`, `recordingUnitIdentifierFormat`/`recordingUnitIdentifierLang`, `minRecordingUnitCode`/`maxRecordingUnitCode`, hiérarchie `children`/`parents` entre unités d'action, `documents`.
**Système exclus** : `creationTime`, `validated`/`validatedAt`/`validatedBy`, `hasChildrens`, `ark`, `formsAvailable`.
**Relations ailleurs** : `recordingUnitList` (côté inverse, alimenté par la feuille UE).

---

## Unité spatiale (Lieu)

Entité : `SpatialUnit.java` (+ `TraceableEntity`). Spec : `SpatialUnitSeeder.SpatialUnitSpecs`. Colonnes attendues : `nom`, `uri type`, `type label`, `institution`, `enfants`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `name` | `name` | Oui | `nom` |
| `category` | `fk_concept_category_id` | Oui | `uri type` / repli `type label` |
| `createdByInstitution` | `fk_institution_id` | Oui | `institution` |
| children/parents (hiérarchie) | `spatial_hierarchy` | Oui (via une autre feuille) | `enfants` et/ou feuille Lieu_rel |
| `createdBy` | `fk_created_by` | Non (toujours codé en dur `SIAMOIS_SYSTEM`) | — |
| `geom` | `geom` (géométrie `MultiPolygon`) | **Non** | — |
| `address` | `address` (jsonb `FullAddress` : libellé, rue, code postal, ville, lon/lat) | **Non** | — |
| `code` | `code` (texte simple) | **Non** | — |
| `ark` | `fk_ark_id` | Non | — |
| `documents` | `spatial_unit_document` | Non | — |
| `creationTime`, `validated`, `validatedAt`, `validatedBy` | — | Non | — |

**Vrais manques** : `geom` (géométrie), `address` (adresse postale/géocodée — vrai champ de formulaire, pas de l'audit), `code` (champ texte simple — pourtant importé côté Unité d'action, incohérence à noter).
**Système exclus** : `ark`, `documents`, `creationTime`, `validated`/`validatedAt`/`validatedBy`. `createdBy` toujours codé en dur, aucune colonne auteur sur cette feuille (contrairement à UE et Mobilier).
**Relations ailleurs** : `recordingUnitList`, `relatedActionUnitList` (côtés inverses).

---

## UE (RecordingUnit)

Entité : `RecordingUnit.java` + `RecordingUnitParent.java` (+ `TraceableEntity`). Spec : `RecordingUnitSeeder.RecordingUnitSpecs`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `identifier` | `identifier` | Oui | `identifiant` |
| `description` | `description` | Oui | `description` |
| `type` | `fk_type` | Oui | `type uri` / `type label` |
| `geomorphologicalCycle` | `fk_geomorphological_cycle` | Oui | `cycle uri` / `cycle label` |
| `geomorphologicalAgent` | `fk_geomorphological_agent` | Oui | `agent uri` / `agent label` |
| `normalizedInterpretation` | `fk_normalized_interpretation` | Oui | `interpretation uri` / `interpretation label` |
| `openingDate` / `closingDate` | `start_date` / `end_date` | Oui | `date d'ouverture` / `date de fermeture` |
| `matrixColor` / `matrixTexture` / `matrixComposition` | — | Oui | `couleur/texture/composition de la matrice` |
| `actionUnit` | `fk_action_unit_id` | Oui | `unite d'action` |
| `spatialUnit` | `fk_spatial_unit_id` | Oui | `unite spatiale` |
| `contributors` | `recording_unit_contributors` | Oui | `contributeurs email` |
| `phases` | `recording_unit_phase` | Oui | `phases` |
| children/parents (hiérarchie) | `recording_unit_hierarchy` | Oui (autre feuille) | `UE_rel` |
| `comments` | `comments` | **Non** | — |
| `erosionShape` / `erosionOrientation` / `erosionProfile` | — | **Non** | — |
| `taq` / `tpq` | — | **Non** | — |
| `chronologicalAttribution` | — | **Non** | — |
| `size.*` (longueur/largeur/épaisseur/unité) | embeddable `RecordingUnitSize` | **Non** | — |
| `altitude.*` (4 bornes + unité) | embeddable `RecordingUnitAltimetry` | **Non** | — |
| `zInf` / `zSup` | `fk_z_inf` / `fk_z_sup` (mesure alternative) | **Non** | — |
| `createdBy` | `fk_created_by` | Non (codé en dur) | — |
| `creationTime` | `creation_time` | Non (toujours `now()`) | — |
| `ark`, `formResponse` | — | Non | — |
| `validated`, `validatedAt`, `validatedBy` | — | Non | — |

**Vrais manques** : `comments`, `erosionShape`/`erosionOrientation`/`erosionProfile`, `taq`/`tpq`, `chronologicalAttribution`, dimensions (`size.*`), altimétrie (`altitude.*`), `zInf`/`zSup`.
**Système exclus** : `creationTime`, `createdBy`, `validated`/`validatedAt`/`validatedBy`, `ark`, `formResponse`.
**Relations ailleurs** : hiérarchie (`UE_rel`), stratigraphie (`Strati_Rel`), `specimenList`, `documents`.

---

## Mobilier (Specimen)

Entité : `Specimen.java` (+ `TraceableEntity`). Spec : `SpecimenSeeder.SpecimenSpecs`.

> ⚠️ **À corriger en priorité — pas un simple manque, un bug** : les colonnes `matiere` et
> `designation` sont bien lues par le parseur (`SpecimenSpecs.type`/`.interpretation`) mais **ne sont
> jamais appliquées à l'entité** par `SpecimenSeeder.buildSpecimen`/`fetchConcepts`. Résultat :
> `Specimen.material` et `Specimen.normalizedInterpretation` restent toujours vides même si le
> fichier importé renseigne ces colonnes.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `fullIdentifier` / `identifier` | — | Oui | `identifiant` |
| `category` | `fk_specimen_category` | Oui | `categorie` (URI seul, pas de repli libellé) |
| `createdByInstitution` | `fk_institution_id` | Oui | `institution` |
| `authors` | `specimen_authors` | Oui | `auteur fiche email` |
| `collectors` | `specimen_collectors` | Oui | `collecteurs emails` |
| `recordingUnit` | `fk_recording_unit_id` | Oui | `unite d'enregistrement` |
| `material` | `specimen_material` | **Non (lu puis jeté — voir avertissement ci-dessus)** | `matiere` |
| `normalizedInterpretation` | `fk_interpretation` | **Non (lu puis jeté — voir avertissement ci-dessus)** | `designation` |
| `materialClass` | `specimen_material_class` | **Non** | — |
| `collectionMethod` | `fk_collection_method` | **Non** | — |
| `sanitaryState` | `fk_sanitary_state` | **Non** | — |
| `collectionDate` | — | **Non** | — |
| `isolationNumber` | `isolat_identifier` | **Non** | — |
| `taq` / `tpq` | — | **Non** | — |
| `otherIdentifier` | — | **Non** | — |
| `chronologicalAttribution` | `fk_chronological_attribution` | **Non** | — |
| `numberOfElements` | — | **Non** | — |
| `weight` | — (`MeasurementAnswer`) | **Non** | — |
| `description` / `comments` | — | **Non** | — |
| `createdBy` | `fk_created_by` | Non (codé en dur) | — |
| `creationTime` | — | Non (toujours `now()`) | — |
| `ark` | — | Non | — |
| `validated`, `validatedAt`, `validatedBy` | — | Non | — |

**Vrais manques (aucune colonne du tout)** : `materialClass`, `collectionMethod`, `sanitaryState`, `collectionDate`, `isolationNumber`, `taq`/`tpq`, `otherIdentifier`, `chronologicalAttribution`, `numberOfElements`, `weight`, `description`, `comments`.
**Colonnes présentes mais silencieusement perdues** : `matiere` → `material`, `designation` → `normalizedInterpretation` (voir avertissement ci-dessus).
**Système exclus** : `ark`, `validated`/`validatedAt`/`validatedBy`, `creationTime`, `createdBy`.
**Relations ailleurs** : `containers`, `phases`, `documents`, hiérarchie `children`/`parents` (pas d'équivalent de `SpatialUnitRelSeeder` pour le mobilier).

---

## Phase

Entité : `Phase.java` (+ `TraceableEntity`). Spec : `PhaseSeeder.PhaseSpecs`.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `identifier` | `identifier` | Oui | `identifiant` |
| `actionUnit` | `fk_action_unit_id` | Oui | `projet` (+ `institution`) |
| `author` | `fk_author_id` | Oui | `auteur` |
| `type` | `fk_type` | Oui | `type uri` |
| `title` | `title` | Oui | `titre` |
| `description` | `description` | Oui | `description` |
| `orderNumber` | `order_number` | Oui | `ordre` |
| `lowerBound` / `upperBound` | `lower_bound` / `upper_bound` | Oui | `borne inferieure` / `borne superieure` |
| `periods` | `phase_period` | **Non** | — |
| `keywords` | `phase_keyword` | **Non** | — |
| `createdBy` | `fk_created_by` | Non (dérivé de `author`) | — |
| `creationTime`, `validated`, `validatedAt`, `validatedBy` | — | Non | — |

**Vrais manques** : `periods` (concepts multiples, table `phase_period`), `keywords` (concepts multiples, table `phase_keyword`).
**Système exclus** : `creationTime`, `validated`/`validatedAt`/`validatedBy`.

---

## Stratigraphie (StratigraphicRelationship)

Entité : `StratigraphicRelationship.java` — n'hérite pas de `TraceableEntity`, aucun champ d'audit/validation à exclure.

| Champ entité | Colonne DB | Mappé ? | Colonne import |
|---|---|---|---|
| `unit1` / `unit2` | `fk_recording_unit_1_id` / `_2_id` | Oui | `us1` / `us2` |
| `concept` | `fk_relationship_concept_id` | Oui | `relation` / repli `relation label` |
| `conceptDirection` | `concept_direction` | Oui | `direction vocabulaire` |
| `isAsynchronous` | `asynchronous` | Oui | `asynchrone` |
| `uncertain` | `uncertain` | Oui | `incertain` |

**Vrais manques** : aucun — les 7 colonnes documentées couvrent l'intégralité des champs persistés.

---

## Relations UE / Relations Lieu

Feuilles purement relationnelles (`parent` / `enfant`) — pas d'entité propre au-delà des deux clés
référencées (UE ou Lieu). Rien à documenter comme champ manquant ici.
