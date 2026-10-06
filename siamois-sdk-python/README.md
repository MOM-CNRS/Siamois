# siamois-sdk

Client Python (sans dépendance QGIS) pour l'API REST SIAMOIS `/api/v1`.

```python
from siamois_sdk import SiamoisClient
c = SiamoisClient("https://siamois.example.org/siamois", lang="fr")
c.login("me@example.org", "motdepasse")             # JWT, pas de refresh : se reconnecter si 401
org = c.organizations().items[0]
projet = c.projects(organization_id=org.id).items[0]
for ue in c.iter_all(lambda **kw: c.recording_units(projet.id, **kw), on_progress=print): ...
```

## Modules
- `client.py` : `SiamoisClient` (auth, organisations, projets, UE, mobilier, formulaires, vocabulaires, PATCH).
- `forms.py` : `layoutJson` + catalogue `fields` -> `FormDefinition`; réponses (enveloppe/valeurs brutes); `build_patch_answers`.
- `flatten.py` : **mise à plat « tableur »** — un champ = une colonne (libellé), vocabulaires affichés par libellé ;
  `cells_to_patch` compare une ligne à sa copie de référence et résout libellés -> identifiants (erreurs ligne par ligne,
  libellé ambigu = erreur, multi-valeurs incomplètes en `add/remove`).
- `errors.py` : exceptions avec messages utilisateur FR (`ConflictError` expose `current_revision`/`server_state`).

## Tests
`python -m unittest discover -s tests -t .`

## Limites connues (API actuelle)
- Pas de géométrie modifiable sur le mobilier (`PATCH /finds/{id}` ne l'accepte pas).
- Références (personnes, UE, lieux…) : lecture seule dans `flatten` (v1) ; seuls les vocabulaires (`*_FROM_FIELD_CODE`) sont éditables.
