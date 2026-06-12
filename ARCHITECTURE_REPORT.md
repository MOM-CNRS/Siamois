# Siamois — Architecture Report

**Date:** 2026-05-31  
**Analyst:** Claude Code (Sonnet 4.6)  
**Branch analyzed:** `feat/us-125`  
**Version:** 0.11.1

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architectural Layers](#3-architectural-layers)
4. [Package Inventory](#4-package-inventory)
5. [Key Design Patterns](#5-key-design-patterns)
6. [Database & Persistence](#6-database--persistence)
7. [Frontend](#7-frontend)
8. [REST API](#8-rest-api)
9. [Security & Authorization](#9-security--authorization)
10. [Testing](#10-testing)
11. [Strengths](#11-strengths)
12. [Improvement Recommendations](#12-improvement-recommendations)
13. [Priority Roadmap](#13-priority-roadmap)

---

## 1. Project Overview

Siamois is an enterprise Spring Boot web application for **archaeological data management**. It handles recording units, action units, specimens, spatial units, stratigraphic relationships, dynamic forms, and vocabulary/thesaurus data.

| Attribute | Value |
|-----------|-------|
| Framework | Spring Boot 3.3.1 |
| Java Version | 17 |
| Build Tool | Maven |
| Database | PostgreSQL 16/17 |
| Main Java classes | ~693 |
| Test classes | ~92 |
| Default language | French |

---

## 2. Technology Stack

### Backend
| Category | Technology |
|----------|-----------|
| Core framework | Spring Boot 3.3.1 |
| Security | Spring Security (BCrypt) |
| Persistence | Spring Data JPA + Hibernate 6.3 |
| Audit | Hibernate Envers |
| DB migrations | Liquibase 4.27.0 |
| Caching | Caffeine 3.2.3 |
| AOP | Spring AOP |
| Geospatial | LocationTech JTS 1.20.0 |
| Excel/Office | Apache POI 5.4.0 |
| Mapping | MapStruct 1.6.3 |
| Boilerplate | Lombok 1.18.24 |
| API docs | SpringDoc OpenAPI 2.6.0 + SpringFox 3.0.0 |

### Frontend
| Category | Technology |
|----------|-----------|
| UI framework | JSF 2.2 + PrimeFaces 6.0.2 (via JoinFaces 5.5.4) |
| Secondary templates | Thymeleaf (login + error pages only) |
| CSS | SCSS compiled via Dart Sass Maven Plugin |
| Icons | Font Awesome 4.7.0 |
| Theme | PrimeFaces Overcast (custom) |

### Infrastructure
| Category | Technology |
|----------|-----------|
| Connection pool | Hikari |
| Test DB | H2 (in-memory) |
| Code quality | SonarCloud |
| Coverage | JaCoCo 0.8.12 |
| Testing | JUnit 4 + Mockito 5.14.2 |

---

## 3. Architectural Layers

The application follows a **layered architecture** with clear package boundaries:

```
┌──────────────────────────────────────────────────┐
│                   UI Layer                        │
│  JSF Backing Beans, REST Controllers, ViewModels  │
│  (fr.siamois.ui)                                  │
├──────────────────────────────────────────────────┤
│              Application / Service Layer          │
│  Business logic, transactions, domain services    │
│  (fr.siamois.domain.services)                     │
├──────────────────────────────────────────────────┤
│               Domain Layer                        │
│  JPA Entities, business rules, domain events      │
│  (fr.siamois.domain.models)                       │
├──────────────────────────────────────────────────┤
│             Infrastructure Layer                  │
│  Repositories, DB init, file storage, ext. APIs   │
│  (fr.siamois.infrastructure)                      │
└──────────────────────────────────────────────────┘
```

---

## 4. Package Inventory

### Domain (`fr.siamois.domain`)
| Package | Role |
|---------|------|
| `models/actionunit/` | Archaeological action units (excavation areas) |
| `models/recordingunit/` | Recording units (stratigraphic contexts) with hierarchy |
| `models/specimen/` | Finds/specimens with form data |
| `models/spatialunit/` | Spatial/geographic units |
| `models/form/` | Dynamic form engine (CustomForm, CustomField, MeasurementAnswer) |
| `models/auth/` | Authentication models (Person, Team roles) |
| `models/vocabulary/` | Concept/thesaurus models |
| `models/events/` | Domain event classes |
| `services/` | Business logic services |
| `services/authorization/` | Permission checking & access control |
| `services/recordingunit/identifier/` | Custom identifier generation |

### Infrastructure (`fr.siamois.infrastructure`)
| Package | Role |
|---------|------|
| `database/repositories/` | Spring Data JPA repositories |
| `database/initializer/` | Database seeding (AdminInitializer, DatasetInitializers) |
| `database/projection/` | Read-only database projections |
| `dataimport/` | Excel/XML data import services |
| `files/` | File storage & document management |
| `api/` | External API integrations (Geoplatform, Thesaurus) |

### UI (`fr.siamois.ui`)
| Package | Role |
|---------|------|
| `bean/` | JSF backing beans (view state) |
| `bean/panel/` | Panel system for tabbed/hierarchical views |
| `bean/dialog/` | Dialog components (CRUD modals) |
| `bean/settings/` | Settings UI beans |
| `api/openapi/v1/` | REST API controllers |
| `form/` | Form rendering & field handling |
| `form/rules/` | Form validation rules |
| `form/savestrategy/` | Entity save strategy implementations |
| `lazydatamodel/` | PrimeFaces lazy-loading data models |
| `table/` | Table definitions & column configurations |
| `viewmodel/` | UI view models |
| `config/` | Spring configuration (Security, JSF, Web) |
| `mapper/` | UI-specific DTO mappers |

### Cross-cutting
| Package | Role |
|---------|------|
| `dto/` | Data Transfer Objects |
| `mapper/` | MapStruct entity ↔ DTO mappers |
| `utils/` | Utility classes |
| `annotations/` | Custom annotations (@ExecutionTimeLogger) |

---

## 5. Key Design Patterns

| Pattern | Where used |
|---------|-----------|
| Repository | Spring Data JPA + JpaSpecificationExecutor |
| Service Layer | `domain/services/` — business logic isolation |
| DTO + Mapper | MapStruct entity ↔ DTO conversions |
| Factory | `PanelFactory` for dynamic UI panel creation |
| Strategy | Form save strategies (`form/savestrategy/`) |
| Event-Driven | Spring ApplicationEvents (ConceptChangeEvent, InstitutionChangeEvent, LoginEvent, LangageChangeEvent) |
| AOP | `ExecutionTimeAspect` for performance logging |
| Specification | JpaSpecificationExecutor for dynamic queries |
| Template Method | Base entities (`TraceableEntity`, `ArkEntity`, `ReferencableEntity`) |

---

## 6. Database & Persistence

### Configuration
| Setting | Value |
|---------|-------|
| Engine | PostgreSQL |
| Pool | Hikari, max 1000 connections |
| DDL auto | `update` (Hibernate managed) |
| Migrations | Liquibase (changelog/versions/) |
| Audit suffix | `_AUD` (Envers) |

### Notable features
- **Envers audit tables** on all major entities — full change history
- **Native SQL** for complex stratigraphic queries
- **PostgreSQL functions** (`pgplsql/`) for concept autocomplete and triggers
- **Geospatial** support via PostGIS (assumed) + JTS
- **H2** used in test profile with Liquibase changesets

### Entity Base Classes
- `TraceableEntity` — audit fields (createdBy, createdAt, lastModified)
- `ArkEntity` — ARK (Archival Resource Key) identifier support
- `ReferencableEntity` — cross-reference support

---

## 7. Frontend

### Architecture
The UI is built on JSF Facelets with PrimeFaces components:
- **Backing beans** (`@Named`, `@ViewScoped`) manage per-view state
- **Panel factory** creates dynamic tabbed panels per entity type
- **Lazy data models** handle paginated DataTable loading
- **Dialog system** provides modal CRUD operations
- **Custom JSF converters** bridge entities to display values

### Layout System
- Master template `template.xhtml` with sidebar navigation
- Tabbed panel system (entity details, stratigraphic, documents, actions)
- SCSS compiled at build time for custom theming

### Dual Templating
Thymeleaf is used **only** for login and error pages alongside JSF — two template engines for a handful of pages.

---

## 8. REST API

**Base path:** `/api/v1/`  
**Documentation:** Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs/`

| Controller | Domain |
|------------|--------|
| `RecordingUnitsControllerApi` | Recording units |
| `ConceptControllerApi` | Vocabulary/concepts |
| `PlaceControllerApi` | Geographic places |
| `OrganizationControllerApi` | Institutions |
| `ProjectControllerApi` | Projects |
| `FindControllerApi` | Specimens/finds |

> **Note:** The majority of these endpoints currently throw `NotImplementedYetException`. The API surface is declared but not implemented.

---

## 9. Security & Authorization

- **Authentication:** Form-based login, email as username
- **Password encoding:** BCrypt
- **CSRF:** Custom form handler
- **Authorization:** Custom `PermissionService` with role-based checks
  - Roles: Institution Manager, Action Manager
  - Resource-level read/write permission checks
  - Custom `writeverifier` implementations per entity type
- **Session timeout:** 125 minutes

---

## 10. Testing

| Metric | Value |
|--------|-------|
| Test classes | ~92 |
| Framework | JUnit 4 + Mockito 5.14.2 |
| Test DB | H2 in-memory |
| Coverage tool | JaCoCo 0.8.12 |
| Integration tests | Separate tag (`integration`), excluded by default |

### What is tested
- Domain services (BookmarkService, ActionUnitService, ContainerService)
- Lazy data models
- Forms (EntityFormContext)
- Utilities (CodeUtils, DateUtils)
- External APIs (ThesaurusAPI, ConceptAPI)
- Data import (OOXMLImportService)

### What is **not** tested (coverage gaps)
- REST controllers
- JSF backing beans
- DTOs
- Event listeners
- Factory classes
- Settings classes

---

## 11. Strengths

- **Clear layer separation** — domain, service, infrastructure, and UI have distinct boundaries
- **Type-safe mappings** — MapStruct prevents runtime conversion errors
- **Full audit trail** — Hibernate Envers covers all major entities
- **Versioned schema** — Liquibase manages all migrations
- **Event-driven decoupling** — Spring events reduce direct dependencies between beans
- **Flexible form engine** — CustomForm/CustomField supports dynamic, runtime-configured forms
- **Caching** — Caffeine with proper eviction strategy on vocabulary lookups
- **Authorization abstraction** — PermissionService centralizes access control logic
- **Geospatial support** — JTS + PostGIS-ready for coordinate/geometry data

---

## 12. Improvement Recommendations

### 12.1 Fix `hibernate.ddl-auto: update` → `validate`
**Risk: HIGH — Data safety**

Running `update` in production allows Hibernate to silently drop columns when entities are renamed and can cause irreversible data loss. Liquibase is already present and should be the sole owner of schema changes.

**Action:** Set `ddl-auto: validate` in production/staging profiles. Let Liquibase manage all schema mutations exclusively.

---

### 12.2 Reduce Hikari Pool Size
**Risk: HIGH — Stability**

`max-pool-size: 1000` will exhaust PostgreSQL's `max_connections` under any real deployment. A single JVM cannot usefully maintain 1000 concurrent DB connections.

**Action:** Set `max-pool-size` to 20–50 and tune based on observed load. Set a corresponding limit on the PostgreSQL side. Monitor pool wait time via Hikari metrics.

---

### 12.3 Complete or Remove Stub REST Endpoints
**Risk: MEDIUM — API contract clarity**

Having an OpenAPI spec full of unimplemented endpoints misleads consumers and pollutes the Swagger UI. It also creates a false impression of the system's integration capabilities.

**Action:** Remove unimplemented endpoints from the OpenAPI spec (or hide them behind a `draft` tag). Implement them properly when the feature is ready.

---

### 12.4 Consolidate DTO and Mapper Layers
**Risk: MEDIUM — Maintainability**

There are currently two mapper packages (`mapper/` and `ui/mapper/`) and three DTO families (`entity`, `field`, `view`). This creates duplication and makes it unclear which mapper/DTO to use when.

**Action:** Define a single DTO tier with explicit naming conventions. Merge mappers into one package. Avoid creating "view DTOs" that only repackage entity DTOs.

---

### 12.5 Add Integration Tests for Controllers and Authorization
**Risk: MEDIUM — Correctness**

The test suite currently excludes REST controllers, backing beans, and authorization logic — exactly where integration-level bugs appear.

**Action:**
- Add `@SpringBootTest` + MockMvc tests for REST controllers
- Add authorization tests that verify unauthorized access is rejected at the service layer (not just the UI)
- Add SonarCloud quality gates for minimum coverage per layer

---

### 12.6 Move Seed Data into Liquibase Changesets
**Risk: MEDIUM — Reproducibility**

Multiple `DatasetInitializer` classes ordered with `@Order` run at startup. This is fragile — ordering can silently break, and initializers are hard to make environment-aware.

**Action:** Move seed data into Liquibase changesets with `context: seed` or `context: test`. This makes initialization versioned, reproducible, and controllable per environment.

---

### 12.7 Enforce Authorization at the Service Layer
**Risk: MEDIUM — Security**

It is unclear which service methods enforce authorization vs. which assume the caller already checked. This can lead to bypasses if a method is called from a new entry point.

**Action:** Apply `@PreAuthorize` (Spring Security method security) to service methods, or at minimum document and test which layer owns each authorization check.

---

### 12.8 Formalize the Identifier Generation Strategy
**Risk: LOW — Maintainability**

`domain/services/recordingunit/identifier/` contains complex custom identifier logic that will grow harder to maintain as new identifier schemes are added.

**Action:** Define an explicit `IdentifierStrategy` interface with one implementation per scheme. Wire via Spring injection. This isolates each strategy and makes it independently testable.

---

### 12.9 Eliminate Dual Templating (JSF + Thymeleaf)
**Risk: LOW — Build complexity**

Thymeleaf is only used for login and error pages. Maintaining two template engines increases build and runtime complexity for marginal benefit.

**Action:** Convert the remaining Thymeleaf pages to JSF Facelets, or — if a frontend migration is planned — replace them with plain HTML served statically.

---

### 12.10 Plan a Gradual Frontend Migration (Long-Term)
**Risk: LOW (now), HIGH (if deferred too long)**

JSF/PrimeFaces 6.0.2 is aging. The REST API foundation exists but is incomplete. Migrating all at once would be prohibitive.

**Action:** Adopt an incremental approach — new features built as SPA pages (React or Vue) consuming the REST API, while existing JSF pages remain until individually replaced. Start by completing the REST API (see 12.3).

---

## 13. Priority Roadmap

| Priority | Item | Effort | Impact |
|----------|------|--------|--------|
| **P0 — Urgent** | Fix `ddl-auto: update` → `validate` | Low | Data safety |
| **P0 — Urgent** | Fix Hikari pool size | Low | Stability |
| **P1 — Short-term** | Complete or remove API stubs | Medium | API clarity |
| **P1 — Short-term** | Consolidate DTO/mapper layers | Medium | Maintainability |
| **P1 — Short-term** | Add integration tests (controllers, auth) | Medium | Correctness |
| **P2 — Medium-term** | Seed data → Liquibase changesets | Medium | Reproducibility |
| **P2 — Medium-term** | Enforce authorization at service layer | Medium | Security |
| **P3 — Low priority** | Formalize identifier strategy pattern | Low | Maintainability |
| **P3 — Low priority** | Remove Thymeleaf dual-template setup | Low | Build simplicity |
| **P4 — Long-term** | Gradual JSF → modern frontend migration | High | UX / Longevity |

---

*Report generated by Claude Code on 2026-05-31.*
