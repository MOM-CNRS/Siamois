# Récupérer une suppression accidentelle grâce à l'audit

Document pour l'ingénieur d'astreinte. Il explique comment retrouver une fiche (ou un projet entier)
supprimée par erreur, à partir des tables d'audit Hibernate Envers. **Il n'y a pas de bouton de
restauration** : la suppression est définitive côté application, la récupération est une opération
manuelle en base. Une corbeille (suppression douce) reste à faire — voir « Limites ».

Faire d'abord une **sauvegarde de la base** (`pg_dump`) avant toute réinsertion.

## 1. Ce que la suppression a fait

| Élément | Après suppression |
|---|---|
| Ligne de la fiche (UE, mobilier, document, phase, étude, contenant, lieu, projet) | Supprimée. Une révision `REVTYPE = 2` est écrite dans la table `_AUD` de l'entité. |
| Valeurs de la fiche | **Conservées** : `hibernate.envers.store_data_at_delete: true` (`application.yaml`), donc la révision de suppression contient toutes les colonnes de la fiche. |
| Qui / quand | Table `revinfo` : `epoch_timestamp`, `fk_person_id` (auteur), `fk_institution_id`. |
| Tables de liaison (hiérarchie d'UE, stratigraphie, documents liés, phases, études…) | Supprimées. Pour les suppressions de projet, **en SQL natif : aucune révision de suppression** ; les anciennes lignes restent dans les tables de liaison `_AUD` (état à la dernière modification). |
| ARK | Supprimé (`ark` est audité). |
| Réponses aux champs additionnels (`form_config_answer`, `custom_field_answer*`) | **Perdues** : ces tables ne sont pas auditées. |
| Fichier d'un document | **Perdu** : supprimé du stockage. Seules les métadonnées restent dans l'audit. À restaurer depuis la sauvegarde du stockage. |
| Configuration d'un projet supprimé (formulaires, compteurs d'identifiants, profils, membres) | `form_config*`, `field_form_config*` sont audités ; compteurs, profils et affectations de membres ne le sont pas. |

## 2. Retrouver la suppression

Les noms de tables sont en minuscules dans PostgreSQL : `recording_unit_aud`, `specimen_aud`,
`siamois_document_aud`, `phase_aud`, `study_aud`, `container_aud`, `spatial_unit_aud`,
`action_unit_aud`. Vérifier les noms exacts de colonnes avec `\d recording_unit_aud`.

Les suppressions récentes, avec l'auteur :

```sql
SELECT r.rev_id,
       to_timestamp(r.epoch_timestamp / 1000.0) AS quand,
       p.username                               AS qui,
       r.fk_institution_id
FROM revinfo r
JOIN person p ON p.person_id = r.fk_person_id
WHERE r.epoch_timestamp > (extract(epoch FROM now() - interval '2 days') * 1000)
ORDER BY r.rev_id DESC;
```

Toutes les fiches supprimées d'un type dans une révision (exemple : unités d'enregistrement) :

```sql
SELECT a.*
FROM recording_unit_aud a
WHERE a.revtype = 2
  AND a.rev = :rev_id;
```

Une suppression de projet produit **une seule révision** (`rev_id`) qui regroupe le projet et tout son
contenu : utiliser le même `:rev_id` pour chaque type.

## 3. Réinsérer une fiche

Les colonnes sont les mêmes que la table d'origine, plus `rev`, `revtype` et les colonnes `*_mod`
(indicateurs de modification), qu'il ne faut pas copier.

```sql
-- 1. Lister les colonnes réelles de la table d'origine
SELECT column_name FROM information_schema.columns
WHERE table_name = 'recording_unit' ORDER BY ordinal_position;

-- 2. Réinsérer depuis la révision de suppression, avec l'id d'origine
INSERT INTO recording_unit (recording_unit_id, <autres colonnes>)
OVERRIDING SYSTEM VALUE
SELECT recording_unit_id, <autres colonnes>
FROM recording_unit_aud
WHERE rev = :rev_id AND revtype = 2 AND recording_unit_id = :id;
```

Points d'attention :

- **Conserver l'id d'origine** (`OVERRIDING SYSTEM VALUE`) : les autres fiches, signets et liens y
  font référence. Ne pas toucher aux séquences, elles ont déjà dépassé cet id.
- **Colonnes à valeur nulle dans la révision** : si une colonne est vide alors qu'elle ne devrait pas
  l'être, prendre la dernière révision précédente (`rev < :rev_id ORDER BY rev DESC LIMIT 1`).
- **Clés étrangères** : réinsérer dans l'ordre inverse de la suppression :
  `ark` → `action_unit` (projet) → `phase`, `container`, `study` → `siamois_document` →
  `recording_unit` → `specimen`.
- **ARK** : réinsérer la ligne `ark_aud` correspondante avant la fiche, puis rattacher `fk_ark_id`.
- **Un identifiant en conflit** (contrainte d'unicité projet + identifiant) : peut arriver si quelqu'un
  a recréé une fiche du même nom entre-temps. Renommer l'une des deux.

## 4. Réinsérer les liens

Pour chaque table de liaison (`recording_unit_hierarchy`, `stratigraphic_relationship`,
`recording_unit_phase`, `recording_unit_document`, `specimen_document`, `study_recording_unit`…),
reprendre dans `<table>_aud` les lignes dont les deux extrémités existent de nouveau et dont la
**dernière révision n'est pas** une suppression :

```sql
SELECT DISTINCT ON (fk_parent_id, fk_child_id) *
FROM recording_unit_hierarchy_aud
WHERE fk_parent_id = :id OR fk_child_id = :id
ORDER BY fk_parent_id, fk_child_id, rev DESC;
```

Ne réinsérer que les lignes dont `revtype <> 2`. Pour une suppression de projet, comme les liens ont
été supprimés en SQL natif, **leur dernière révision est un ajout ou une modification** : c'est
normal et c'est bien l'état à restaurer.

## 5. Ce qu'il faut reconstituer autrement

- **Fichiers de documents** : depuis la sauvegarde du stockage. Le nom et le chemin sont dans
  `siamois_document_aud` (colonnes de fichier de la révision de suppression).
- **Réponses aux champs additionnels** : aucune source dans la base. Si elles comptent, seule la
  sauvegarde complète de la base les contient (`pg_restore` dans une base temporaire, puis copie).
- **Projet supprimé** : recréer aussi les profils de projet (`profile`, `person_profile_assignment`)
  et les compteurs d'identifiants (`identifier_counter`, `recording_unit_id_label`) ; ils ne sont pas
  audités. Ils se recréent en rouvrant la gestion des membres du projet, mais les numéros suivants
  repartiront de zéro : vérifier qu'ils ne produisent pas d'identifiants déjà pris.

## 6. Après la restauration

1. Vider les caches applicatifs (redémarrage du backend suffit).
2. Vérifier la fiche dans l'application et ses compteurs (onglets, listes).
3. Noter l'opération (date, `rev_id` restauré, auteur de la suppression).

## Limites connues et suite prévue

- Pas de corbeille, pas d'écran d'historique pour une fiche supprimée.
- Pas de journal de suppression applicatif : la requête de la section 2 sur `revinfo` en tient lieu.
- À étudier : suppression douce (`deleted_at`), déplacement des fichiers vers une corbeille plutôt
  que leur effacement, audit des réponses aux champs additionnels.
