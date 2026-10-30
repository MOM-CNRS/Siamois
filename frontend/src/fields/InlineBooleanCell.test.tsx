import {afterEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {InlineBooleanCell} from "./InlineBooleanCell";
import type {FieldResource} from "./types";

vi.mock("../notify/NotifyProvider", () => ({ useToastOnChange: () => {} }));

const field = { id: "7", resourceType: "fields", label: "Ok", answerType: "BOOLEAN", isSystemField: false } as FieldResource;

let root: Root | null = null;
afterEach(() => { act(() => root?.unmount()); root = null; document.body.innerHTML = ""; });

describe("InlineBooleanCell", () => {
  it("saves the toggled value straight from the checkbox click", async () => {
    (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    const onSave = vi.fn().mockResolvedValue(undefined);
    const onSaved = vi.fn();
    const host = document.createElement("div");
    document.body.appendChild(host);
    root = createRoot(host);
    await act(async () => {
      root!.render(<InlineBooleanCell field={field} rowId={3} value={false} readOnly={false} onSave={onSave} onSaved={onSaved} />);
    });
    await act(async () => {
      (host.querySelector("input[type=checkbox]") as HTMLElement).click();
    });
    expect(onSave).toHaveBeenCalledWith(3, { "7": { value: true } }, true);
    expect(onSaved).toHaveBeenCalled();
  });
});
