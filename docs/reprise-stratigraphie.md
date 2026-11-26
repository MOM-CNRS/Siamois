# Reprise de la stratigraphie : audit et correspondance (étape 2.0)

Étape bloquante du lot 2 (docs/modele-relations.md, §6.2) : ce que fait l'existant, le sens des anciennes colonnes,
les concepts réellement utilisés et leur devenir. **À valider avant toute reprise.**

> Pas de base réelle disponible : l'inventaire porte sur les bases locales (`siamois_kition_test` et
> `siamois_import_test`, ~310 liens typés chacune ; `siamois_demo`, 38 ; `siamois_nok`, 27 ; `siamois`, 59 dont 1 typé ;
> `siamois_projet_defaut`, 1). La requête d'inventaire (§5) est à rejouer sur une copie de la base de production.

## 1. Audit de l'existant

| Élément | Ce qu'il fait | Devenir |
|---|---|---|
| Trigger `prevent_reverse_relationship` (`order_strat_relationship`, posé par `StratiTriggerInitializer`) | `BEFORE INSERT` : **ignore en silence** l'insertion d'une paire dont l'inverse existe déjà. Rien d'autre (ni boucle, ni projet, ni type) | Remplacé, en mieux : A → B puis B → A est une boucle, **refusée explicitement** (ordre, `no_cycle`) ; la synchronie range la paire et l'index unique refuse le doublon ; ordre et synchronie sur une même paire sont exclusifs. Rien n'est perdu |
| Clé primaire (`fk_recording_unit_1_id`, `fk_recording_unit_2_id`) | Une seule relation par paire, quel que soit le sens (avec le trigger) | L'ordre devient complexe : plusieurs liens entre deux UE seront possibles (plusieurs qualificatifs) |
| `RecordingUnitStratigraphyWriteService` (API) | Même projet, pas sur soi, doublon dans les deux sens, boucle en Java (liens asynchrones) | Repris par `RelationLinkService` |
| `StratigraphicRelationshipService`, `utils/stratigraphy/*` (`Stratifiant`, `SynchronousGroupBuilder`, `StratigraphyOrderRelationshipProcessor`) | Ancien code de calcul, concepts « SIAMOIS_SYNCHRONOUS / ASYNCHRONOUS » | **Aucun appelant** : code mort, retiré à l'étape 2.2 |
| Import Excel (`OOXMLImportService` → `RecordingUnitStratiRelSeeder`) | Recopie `us1`, `us2`, « direction vocabulaire », « asynchrone », « incertain » tels quels, concept pris dans la liste `SIARU.STRATI` | Écrira dans `relation_link` à l'étape 2.2 ; **le sens dépend du fichier** (voir §2) |

## 2. Sens des anciennes colonnes

Fixé par l'écriture de l'API et déjà couvert par des tests (`RecordingUnitStratigraphyWriteServiceTest` :
`aPosteriorUnitIsStoredAfterTheOwner`, `anAnteriorUnitIsStoredBeforeTheOwner`), et suivi par toute la lecture
(`RelationField`, `RelationFieldService`, graphe) :

- `fk_recording_unit_1_id` = **l'UE antérieure** (la plus ancienne), `fk_recording_unit_2_id` = **l'UE postérieure** ;
- `asynchronous` vrai (ou null) = ordre, faux = synchronie ;
- `concept_direction` vrai = le concept se lit **depuis l'UE 1** (« UE 1 est recouverte par UE 2 »), faux = depuis
  l'UE 2 (« UE 2 recouvre UE 1 »).

Les données locales confirment cette convention : dans `kition`, les 202 « est recouverte par » (branche postérieure)
se lisent depuis l'UE 1 et les 57 « recouvre » (branche antérieure) depuis l'UE 2, sans exception.

**Exception relevée** : `siamois_demo` a 2 liens « antérieur à » lus depuis l'UE 2 (« UE 2 antérieur à UE 1 »), qui
contredisent l'ordre des colonnes. Ils viennent d'un import dont le fichier ne suivait pas la convention.

## 3. Règle de reprise

Pour chaque ligne de `stratigraphic_relationship` :

1. `asynchronous` vrai ou null → un lien de la définition **ordre** : source = UE 2 (postérieure), cible = UE 1.
   `asynchronous` faux → un lien de la définition **synchronie**, paire rangée.
2. **Qualificatif**, toujours vu de la source (l'UE postérieure) :
   - concept lu depuis l'UE 2 (`concept_direction` faux) → repris tel quel ;
   - concept lu depuis l'UE 1 (vrai) → remplacé par son correspondant (« est recouverte par » → « recouvre ») ;
   - sans correspondant connu → repris tel quel **et listé dans le rapport** (son sens est alors inversé) ;
   - `concept_direction` null → déduit de la branche du concept (antérieure : tel quel ; postérieure : correspondant).
3. `uncertain` → la colonne `uncertain` (null → faux). Le projet du lien = le projet des deux UE.
4. Les concepts sont désignés par leur appartenance aux branches des field codes `SIARU.STRATI.*`, puis par
   `external_id` pour les paires du thésaurus SIAMOIS, jamais par identifiant de base.

## 4. Table de correspondance

### Branches antérieure et postérieure (thésaurus SIAMOIS) — paires du qualificatif de l'ordre

| Qualificatif enregistré (branche antérieure) | Miroir (branche postérieure) | Liens locaux |
|---|---|---|
| coupe (1369) | est coupée par (1374) | 6 + 1 |
| recouvre (1370) | est recouverte par (1375) | 57 + 202 (kition), 1 + 1 (nok), 1 (siamois) |
| s'appuie sur (1371) | est appuyée par (1376) | 0 + 2 (nok) |
| comble (1372) | est comblée par (1377) | 0 |

### Branche synchrone

| Concept | Liens locaux |
|---|---|
| équivalent à (1379) | 26 |
| contemporain de (1380) | 20 + 1 + 1 |
| se lie à (1381), Encastré (1385) | 0 |

### Ancienne liste commune `SIARU.STRATI`

| Concept | Correspondant proposé | Remarque |
|---|---|---|
| sur (1359) | sous (1360) | |
| coupe (1362) | est coupée par (1363) | |
| regroupe (1364) | est regroupée dans (1365) | relève plutôt de l'inclusion (à trancher) |
| englobe (1366) | est englobée par (1367) | idem |
| équivalent (1361) | — | synchrone |
| Adossé (1382), Liaisonné (1383), Encastré (1384) | — | sans sens de lecture : repris tels quels |
| antérieur à / postérieur à / synchrone avec (vocabulaire de `siamois_demo`) | — | redisent seulement l'ordre (voir décision 2) |

## 5. Requête d'inventaire (à rejouer sur une copie de la production)

```sql
WITH RECURSIVE branch(top, concept_id) AS (
    SELECT field_code, fk_concept_id FROM concept_field_config WHERE field_code LIKE 'SIARU.STRATI%'
  UNION
    SELECT b.top, h.fk_child_concept_id FROM branch b JOIN concept_hierarchy h ON h.fk_parent_concept_id = b.concept_id
)
SELECT (SELECT string_agg(DISTINCT b.top, ',') FROM branch b WHERE b.concept_id = r.fk_relationship_concept_id) AS branches,
       c.external_id,
       (SELECT l.label FROM concept_label l WHERE l.fk_concept_id = c.concept_id AND l.lang_code = 'fr' LIMIT 1) AS libelle,
       r.asynchronous, r.concept_direction, count(*)
FROM stratigraphic_relationship r LEFT JOIN concept c ON c.concept_id = r.fk_relationship_concept_id
GROUP BY 1, 2, 3, 4, 5 ORDER BY 6 DESC;
```

## 6. Décisions (validées le 26/11/2026)

1. **Ordre et synchronie sont deux relations complexes à qualificatif facultatif.** Le qualificatif de l'ordre vient
   de la branche antérieure, celui de la synchronie de la branche synchrone (symétrique : pas de miroir).
2. **« antérieur à », « postérieur à », « synchrone avec »** ne disent rien de plus que la relation : repris **sans
   qualificatif**.
3. **Le qualificatif enregistré est toujours le membre de sa paire vu de l'UE postérieure** (« recouvre », « sur »),
   quel que soit `concept_direction`. Celui-ci ne sert plus qu'au rapport : un lien dont le sens de lecture contredit
   les colonnes y est listé, et l'ordre des colonnes fait foi.
4. **« regroupe », « englobe » et leurs inverses relèvent de la hiérarchie** : leurs liens **ne sont pas repris**
   aujourd'hui. Ils restent dans l'ancienne table, gardée en lecture seule ; leur reprise (vers une hiérarchie) devra
   précéder la suppression de cette table.
5. **Les paires miroir sont trouvées par le sens des libellés**, et non par `external_id` : la même table de paires
   s'applique à chaque vocabulaire (thésaurus SIAMOIS ou propre à une institution), au sein d'un même vocabulaire et
   d'un même groupe de branches (antérieure ↔ postérieure, ou ancienne liste ↔ elle-même). Un concept sans paire est
   repris tel quel et listé dans le rapport.

| Vu de l'UE postérieure (enregistré) | Miroir, vu de l'UE antérieure |
|---|---|
| coupe | est coupée par |
| recouvre | est recouverte par |
| s'appuie sur | est appuyée par |
| comble | est comblée par |
| sur | sous |

Les libellés sont comparés en minuscules et sans accents. Les paires vont dans les règles par défaut de la plateforme
de l'ordre ; une institution pourra en déclarer d'autres dans ses propres règles.

## 7. Les champs et les endpoints actuels

- Les champs système restent : **-328 « Antérieur »** (sur une UE, les UE qui lui sont antérieures) est le **côté
  source** de l'ordre, porté par l'UE postérieure, et son vocabulaire (`SIARU.STRATI.ANTERIOR`) est celui du
  qualificatif ; **-329 « Postérieur »** en est le miroir (`SIARU.STRATI.POSTERIOR`) ; **-330 « Synchrone »** est
  les deux côtés de la synchronie. Chaque champ est aussi le champ qualificatif de sa définition.
- Le front écrit par `/api/v1/relation-links`, en désignant le champ de saisie (`field` : -328, -329 ou -330),
  qui donne la définition et le côté ; il modifie et supprime un lien par son identifiant, que la lecture donne
  (`qualifier.linkId`). Les endpoints `/recording-units/{id}/stratigraphic-relationships` sont retirés. La saisie en
  masse garde son format (`position`) et le traduit côté serveur.
- Tout le reste lit et écrit `relation_link` : fiche, listes (aperçus, filtre, tri, compteurs), graphe, import Excel,
  export, duplication d'une UE, garde de suppression. L'ancienne table n'est plus écrite ; ses clés étrangères
  passent en suppression en cascade et son trigger est retiré.

## 8. Reprise (changelog 2026-11-26-2) et rapport

Le changelog crée les deux définitions, leurs règles par défaut de la plateforme et les paires miroir, puis reprend
les liens ; une notice donne les comptes (relations anciennes, liens d'ordre et de synchronie écrits, laissés dans
l'ancienne table). Essai sur une copie de `siamois_kition_test` : 311 relations, 265 liens d'ordre (259 « recouvre »,
6 « coupe ») et 46 de synchronie, aucune laissée ; 6 paires miroir trouvées. Les cas particuliers sont couverts par
`StratigraphyTakeoverPostgresTest`.

Le rapport, à lire sur la base reprise :

```sql
-- Les relations laissées dans l'ancienne table (hiérarchie, ou deux projets).
SELECT r.* FROM stratigraphic_relationship r
WHERE NOT EXISTS (SELECT 1 FROM relation_link l
                  WHERE (l.source_id = r.fk_recording_unit_2_id AND l.target_id = r.fk_recording_unit_1_id)
                     OR (l.source_id = LEAST(r.fk_recording_unit_1_id, r.fk_recording_unit_2_id)
                         AND l.target_id = GREATEST(r.fk_recording_unit_1_id, r.fk_recording_unit_2_id)));

-- Les liens d'ordre dont le qualificatif n'a pas de paire, et dont le sens de lecture était depuis l'UE antérieure :
-- leur qualificatif est peut-être à inverser.
SELECT r.*, l.relation_link_id FROM stratigraphic_relationship r
JOIN relation_link l ON l.source_id = r.fk_recording_unit_2_id AND l.target_id = r.fk_recording_unit_1_id
JOIN relation_definition d ON d.relation_definition_id = l.fk_relation_definition_id AND d.fk_source_field_id = -328
WHERE r.concept_direction AND l.fk_qualifier_concept_id = r.fk_relationship_concept_id
  AND NOT EXISTS (SELECT 1 FROM relation_rule_mirror_concept m
                  WHERE m.fk_concept_id = l.fk_qualifier_concept_id OR m.fk_mirror_concept_id = l.fk_qualifier_concept_id);
```
