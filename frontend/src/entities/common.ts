// Wire fragments shared by several entity types' resources — one definition, imported by each
// entity folder (which must not import one another).

export interface ResolvedConcept {
  resourceType: string;
  id: string;
  externalUrl?: string | null;
  resolvedLabel?: string | null;
}

export interface OrganizationIdentifier {
  resourceType: string;
  id: string;
}

// A row's project on an organization-wide list (ResourceRef server side, set by
// OrganizationListsControllerApi only) — absent on project-scoped lists and on details.
export interface ProjectRef {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export interface ProjectTableColumnDefault {
  columnId: string;
  fieldId: string;
  visible: boolean;
  order: number;
}
