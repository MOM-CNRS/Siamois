import { createEntityApi } from "../createEntityApi";
import type { ContainerDetail, ContainerSummary } from "./types";

// Mirrors ContainerCreateRequest's required pair.
export interface ContainerCreateBody {
  projectId: string;
  typeId: string;
}

const api = createEntityApi<ContainerSummary, ContainerDetail, ContainerCreateBody>("containers");

export const listContainers = api.list;
export const getContainer = api.get;
export const patchContainerAnswers = api.patchAnswers;
export const patchContainer = api.patch;
export const createContainer = api.create;
