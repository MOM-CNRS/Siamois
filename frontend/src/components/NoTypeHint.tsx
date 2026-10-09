import { apiUrl } from "../api/basePath";
import type { CreatableKind } from "../entities/types";
import { t } from "../i18n";

// The configurable table (ConfigurableTable) each creatable entity's types are declared on.
const TABLE_OF: Record<CreatableKind, string> = {
  recordingUnit: "UE",
  find: "MOBILIER",
  phase: "PHASE",
  container: "CONTENANT",
  document: "DOCUMENT",
};

/**
 * What a creation form shows in place of its type picker when the project has declared no type for the
 * table: nothing can be created until one is, so it points to where they are configured (the project's
 * table settings, opened on that table). A full page navigation: the settings are a JSF page.
 */
export function NoTypeHint({ kind, projectId }: { kind: CreatableKind; projectId: string | number }) {
  return (
    <span className="sia-create-form-hint">
      {t("create.noTypeDeclared")}{" "}
      <a href={apiUrl(`/settings/project/${projectId}/tables?table=${TABLE_OF[kind]}`)}>{t("create.configureTypes")}</a>
    </span>
  );
}
