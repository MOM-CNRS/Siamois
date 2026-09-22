import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { WriteModeProvider, useCanEdit, useWriteMode } from "./writeMode";

function ShowWriteMode() {
  return <span>{String(useWriteMode())}</span>;
}

function ShowCanEdit({ entity }: { entity?: { _permissions?: { canEdit?: boolean } } | null }) {
  return <span>{String(useCanEdit(entity))}</span>;
}

describe("useWriteMode", () => {
  it("is false with no provider — read-only is the safe default", () => {
    expect(renderToStaticMarkup(<ShowWriteMode />)).toContain("false");
  });

  it("reports the provided mode", () => {
    expect(
      renderToStaticMarkup(
        <WriteModeProvider value>
          <ShowWriteMode />
        </WriteModeProvider>,
      ),
    ).toContain("true");
  });
});

describe("useCanEdit", () => {
  function render(writeMode: boolean, entity?: { _permissions?: { canEdit?: boolean } } | null) {
    return renderToStaticMarkup(
      <WriteModeProvider value={writeMode}>
        <ShowCanEdit entity={entity} />
      </WriteModeProvider>,
    );
  }

  it("needs BOTH the global mode and the user's own right (headerEditControls.xhtml, bug #448)", () => {
    expect(render(true, { _permissions: { canEdit: true } })).toContain("true");
    expect(render(false, { _permissions: { canEdit: true } })).toContain("false");
    expect(render(true, { _permissions: { canEdit: false } })).toContain("false");
    expect(render(false, { _permissions: { canEdit: false } })).toContain("false");
  });

  it("treats an entity that carries no _permissions as editable, leaving enforcement to the API", () => {
    expect(render(true, {})).toContain("true");
    expect(render(true, { _permissions: {} })).toContain("true");
  });

  it("is false with no entity at all", () => {
    expect(render(true, null)).toContain("false");
    expect(render(true, undefined)).toContain("false");
  });
});
