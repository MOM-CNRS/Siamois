import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createContainer } from "./api";

// ContainerNewUnitForm only requires a type; spatial unit, dimensions and weight are edited
// afterwards on the fiche.
export function ContainerCreateForm(ctx: CreateFormContext) {
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="container"
      title="Nouveau contenant"
      typesSegment="container-types"
      create={(projectId, typeId) => createContainer({ projectId, typeId })}
    />
  );
}
