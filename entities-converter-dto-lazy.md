# Entités avec converter vers DTO ET relations LAZY

Liste des entités JPA qui possèdent à la fois :
- un **converter/mapper MapStruct vers un DTO** (`fr.siamois.mapper.*`, `Converter<Entity, EntityDTO>`)
- au moins une **relation chargée en LAZY**

> Rappel JPA : `@OneToMany` et `@ManyToMany` sont **LAZY par défaut** (même sans `fetch = FetchType.LAZY`).
> `@ManyToOne` et `@OneToOne` sont EAGER par défaut — ils ne sont LAZY que si explicitement annotés.

---

## Entités concernées

### ActionUnit → `ActionUnitDTO`, `ActionUnitSummaryDTO`
Mappers : `ActionUnitMapper`, `ActionUnitSummaryMapper`
Relations LAZY :
- `Set<Document> documents` — `@OneToMany` (lazy par défaut)
- `Set<ActionUnitFormMapping> formsAvailable` — `@OneToMany(fetch = LAZY)`
- `Set<ActionCode> secondaryActionCodes` — `@ManyToMany(fetch = LAZY)`
- `Set<ActionUnit> children` — `@ManyToMany` (lazy par défaut)
- `Set<ActionUnit> parents` — `@ManyToMany(mappedBy = "children")`
- `Set<RecordingUnit> recordingUnitList` — `@OneToMany(mappedBy = "actionUnit")`
- `Set<SpatialUnit> spatialContext` — `@ManyToMany` (lazy par défaut)

### Bookmark → `BookmarkDTO`
Mapper : `BookmarkMapper`
Relations LAZY :
- `Person person` — `@ManyToOne(fetch = LAZY)`
- `Institution institution` — `@ManyToOne(fetch = LAZY)`

### Container → `ContainerDTO`
Mapper : `ContainerMapper`
Relations LAZY :
- `Set<Container> children` — `@OneToMany(mappedBy = "parent", fetch = LAZY)`
- `Container parent` — `@ManyToOne(fetch = LAZY)`
- (autre `@ManyToOne(fetch = LAZY)`)

### Institution → `InstitutionDTO`
Mapper : `InstitutionMapper`
Relations LAZY :
- `Set<Person> managers` — `@ManyToMany(fetch = LAZY)`

### Phase → `PhaseDTO`
Mapper : `PhaseMapper`
Relations LAZY :
- `Set<Concept> periods` — `@ManyToMany` (lazy par défaut)
- `Set<Concept> keywords` — `@ManyToMany` (lazy par défaut)

### RecordingUnit → `RecordingUnitDTO`, `RecordingUnitSummaryDTO`
Mappers : `RecordingUnitMapper`, `RecordingUnitSummaryMapper`
Relations LAZY :
- `Set<StratigraphicRelationship> relationshipsAsUnit1` — `@OneToMany(mappedBy = "unit1", fetch = LAZY)`
- `Set<StratigraphicRelationship> relationshipsAsUnit2` — `@OneToMany(mappedBy = "unit2", fetch = LAZY)`
- `Set<RecordingUnit> children` — `@ManyToMany(fetch = LAZY)`
- `Set<RecordingUnit> parents` — `@ManyToMany(mappedBy = "children", fetch = LAZY)`
- `List<Person> contributors` — `@ManyToMany(fetch = LAZY)`
- `Set<Specimen> specimenList` — `@OneToMany(mappedBy = "recordingUnit")`
- `Set<Phase> phases` — `@ManyToMany(fetch = LAZY)`
- `Set<Document> documents` — `@OneToMany(fetch = LAZY)`

### SpatialUnit → `SpatialUnitDTO`, `SpatialUnitSummaryDTO`
Mappers : `SpatialUnitMapper`, `SpatialUnitSummaryMapper`
Relations LAZY :
- `Set<Document> documents` — `@OneToMany(fetch = LAZY)`
- `Set<SpatialUnit> children` — `@ManyToMany(fetch = LAZY)`
- `Set<SpatialUnit> parents` — `@ManyToMany(mappedBy = "children", fetch = LAZY)`
- `Set<RecordingUnit> recordingUnitList` — `@OneToMany(mappedBy = "spatialUnit")`
- `Set<ActionUnit> relatedActionUnitList` — `@ManyToMany(mappedBy = "spatialContext")`

### Specimen → `SpecimenDTO`, `SpecimenSummaryDTO`
Mappers : `SpecimenMapper`, `SpecimenSummaryMapper`
Relations LAZY :
- `Set<Document> documents` — `@OneToMany(fetch = LAZY)`
- `List<Person> authors` — `@ManyToMany` (lazy par défaut)
- `List<Person> collectors` — `@ManyToMany` (lazy par défaut)
- `Set<Specimen> children` — `@ManyToMany(fetch = LAZY)`
- `Set<Specimen> parents` — `@ManyToMany(mappedBy = "children", fetch = LAZY)`
- `Set<Container> containers` — `@ManyToMany(fetch = LAZY)`
- `Set<Phase> phases` — `@ManyToMany(fetch = LAZY)`
- (plus `@ManyToMany` lazy par défaut et un `@ManyToOne(fetch = LAZY)`)

### StratigraphicRelationship → `StratigraphicRelationshipDTO`
Mapper : `StatigraphicRelationshipMapper`
Relations LAZY :
- `RecordingUnit unit1` — `@ManyToOne(fetch = LAZY, optional = false)`
- `RecordingUnit unit2` — `@ManyToOne(fetch = LAZY, optional = false)`

---

## Entités avec converter mais SANS relation LAZY (exclues)

- **ActionCode** → `ActionCodeDTO` : seul `@ManyToOne(fetch = EAGER)`
- **Concept** → `ConceptDTO` : seul `@ManyToOne(fetch = EAGER)`
- **CustomFieldMeasurement** → `CustomFieldMeasurementDTO` : `@ManyToOne(fetch = EAGER)`
- **MeasurementAnswer** → `MeasurementAnswerDTO` : `@ManyToOne` (EAGER par défaut)
- **Person** → `PersonDTO` : aucune relation
- **UiTableView** → `UITableViewDTO` : `@ManyToOne` (EAGER par défaut)
- **UnitDefinition** → `UnitDefinitionDTO` : `@ManyToOne(fetch = EAGER)`

> Note : `PlaceSuggestionMapper` (`SpatialUnitDTO → PlaceSuggestionDTO`) et `SpecimenProjectionMapper`
> (`SpecimenProjection → SpecimenDTO`) ne partent pas d'une entité JPA et ne sont donc pas listés.