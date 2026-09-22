import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { SelectManyConceptRenderer, SelectOneConceptRenderer, SelectOneSpatialUnitRenderer } from "./renderers";
import type { FieldResource } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./optionSources", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./optionSources")>();
  return {
    ...actual,
    optionSourceFor: vi.fn(actual.optionSourceFor),
  };
});
import { optionSourceFor } from "./optionSources";
const mockedOptionSourceFor = vi.mocked(optionSourceFor);

function conceptField(overrides: Partial<FieldResource> = {}): FieldResource {
  return {
    id: "-118",
    resourceType: "fields",
    label: "Statut",
    answerType: "SELECT_ONE_FROM_FIELD_CODE",
    isSystemField: true,
    fieldCode: "SIARU.STATUS",
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  mockedOptionSourceFor.mockReset();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

describe("SelectOneConceptRenderer", () => {
  it("shows a ResourceRef value (from an answers-map list row) as its label", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={{ resourceId: "7", resourceType: "concepts", label: "En cours" }}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.value).toBe("En cours");
  });

  it("shows a ResolvedConceptResource value (a flat entity property) as its label too", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={{ resourceType: "concepts", id: "9", resolvedLabel: "Sondage" }}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.value).toBe("Sondage");
  });

  it("shows nothing selected for a null value", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={null}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.value).toBe("");
  });

  it("disables the input when readOnly", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={null}
          readOnly
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.disabled).toBe(true);
  });

  it("builds its option loader from the field and organizationId via optionSourceFor", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={null}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    expect(mockedOptionSourceFor).toHaveBeenCalledWith(conceptField(), 100);
  });

  it("builds no loader when organizationId is absent", async () => {
    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={null}
          readOnly={false}
          required={false}
          onChange={() => {}}
        />,
      );
    });
    expect(mockedOptionSourceFor).not.toHaveBeenCalled();
  });
});

describe("SelectManyConceptRenderer", () => {
  it("shows each item of a ResourceRef[] value as a chip label", async () => {
    act(() => {
      root.render(
        <SelectManyConceptRenderer
          field={conceptField({ id: "-115", answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" })}
          value={[
            { resourceId: "1", resourceType: "concepts", label: "Néolithique" },
            { resourceId: "2", resourceType: "concepts", label: "Antiquité" },
          ]}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    expect(container.textContent).toContain("Néolithique");
    expect(container.textContent).toContain("Antiquité");
  });

  it("shows nothing selected for an empty/absent value", async () => {
    act(() => {
      root.render(
        <SelectManyConceptRenderer
          field={conceptField({ id: "-115", answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" })}
          value={undefined}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    expect(container.querySelector(".p-autocomplete-token")).toBeFalsy();
  });
});

describe("SelectOneSpatialUnitRenderer", () => {
  it("shows a PlaceLightResource value (mainLocation's flat shape, id+name) as its label", async () => {
    act(() => {
      root.render(
        <SelectOneSpatialUnitRenderer
          field={{ id: "-108", resourceType: "fields", label: "Commune", answerType: "SELECT_ONE_SPATIAL_UNIT", isSystemField: true }}
          value={{ resourceType: "places", id: "42", name: "Lyon" }}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });
    const input = container.querySelector("input") as HTMLInputElement;
    expect(input.value).toBe("Lyon");
  });
});

describe("picker opening (search on focus)", () => {
  async function flush() {
    await act(async () => {
      for (let i = 0; i < 3; i++) await new Promise((r) => setTimeout(r, 0));
    });
  }

  it("runs an empty-query search as soon as a concept picker gets focus, and shows the options", async () => {
    const loader = vi.fn().mockResolvedValue([
      { id: "7", label: "En cours" },
      { id: "8", label: "Terminé" },
    ]);
    mockedOptionSourceFor.mockReturnValue(loader);

    act(() => {
      root.render(
        <SelectOneConceptRenderer
          field={conceptField()}
          value={null}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });

    const input = container.querySelector("input") as HTMLInputElement;
    await act(async () => {
      input.focus();
    });
    await flush();

    expect(loader).toHaveBeenCalledWith("");
    // PrimeReact's AutoComplete overlay portals into document.body.
    expect(document.body.textContent).toContain("En cours");
  });

  it("does not search on focus for a spatial-unit picker, whose endpoint rejects a blank query", async () => {
    const loader = vi.fn().mockResolvedValue([]);
    mockedOptionSourceFor.mockReturnValue(loader);

    act(() => {
      root.render(
        <SelectOneSpatialUnitRenderer
          field={conceptField({ answerType: "SELECT_ONE_SPATIAL_UNIT", fieldCode: null })}
          value={null}
          readOnly={false}
          required={false}
          onChange={() => {}}
          organizationId={100}
        />,
      );
    });

    const input = container.querySelector("input") as HTMLInputElement;
    await act(async () => {
      input.focus();
    });
    await flush();

    expect(loader).not.toHaveBeenCalled();
  });
});
