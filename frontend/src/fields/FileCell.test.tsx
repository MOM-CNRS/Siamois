import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { FileCell, formatFileSize } from "./FileCell";
import { fetchDocumentFile, removeDocumentFile, uploadDocumentFile } from "../entities/document/api";

vi.mock("../entities/document/api", () => ({
  fetchDocumentFile: vi.fn(),
  removeDocumentFile: vi.fn(),
  uploadDocumentFile: vi.fn(),
}));

const mockedUpload = vi.mocked(uploadDocumentFile);
const mockedRemove = vi.mocked(removeDocumentFile);
const mockedFetch = vi.mocked(fetchDocumentFile);

let container: HTMLDivElement;
let root: Root;

function render(props: Partial<Parameters<typeof FileCell>[0]> = {}) {
  const onChanged = vi.fn();
  act(() => {
    root.render(<FileCell documentId={7} file={null} editable onChanged={onChanged} {...props} />);
  });
  return onChanged;
}

const flush = () => act(async () => { await new Promise((r) => setTimeout(r, 0)); });
const button = (label: string) =>
  Array.from(container.querySelectorAll("button")).find((b) => b.textContent?.includes(label)) as HTMLButtonElement | undefined;

beforeEach(() => {
  vi.resetAllMocks();
  mockedFetch.mockResolvedValue(new Blob(["abc"]));
  Object.assign(URL, { createObjectURL: vi.fn(() => "blob:preview"), revokeObjectURL: vi.fn() });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("formatFileSize", () => {
  it("scales bytes, KB and MB", () => {
    expect(formatFileSize(null)).toBe("");
    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(2048)).toBe("2.0 KB");
    expect(formatFileSize(3 * 1024 * 1024)).toBe("3.0 MB");
  });
});

describe("FileCell", () => {
  it("offers only the upload when the document has no file, and sends the chosen one", async () => {
    mockedUpload.mockResolvedValue(undefined);
    const onChanged = render();
    expect(button("Envoyer un fichier")).toBeDefined();
    expect(button("Télécharger")).toBeUndefined();

    const file = new File(["abc"], "plan.pdf", { type: "application/pdf" });
    const input = container.querySelector<HTMLInputElement>('[data-testid="file-cell-input"]')!;
    await act(async () => {
      Object.defineProperty(input, "files", { value: [file], configurable: true });
      input.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await flush();

    expect(mockedUpload).toHaveBeenCalledWith(7, file);
    expect(onChanged).toHaveBeenCalled();
  });

  it("shows the name and size, and no editing action to a user who cannot edit", () => {
    render({ editable: false, file: { fileName: "plan.pdf", mimeType: "application/pdf", size: 2048 } });
    expect(container.textContent).toContain("plan.pdf");
    expect(container.textContent).toContain("2.0 KB");
    expect(button("Télécharger")).toBeDefined();
    expect(button("Remplacer")).toBeUndefined();
    expect(button("Retirer")).toBeUndefined();
  });

  it("asks to confirm before removing the file", async () => {
    mockedRemove.mockResolvedValue(undefined);
    const onChanged = render({ file: { fileName: "plan.pdf", mimeType: "application/pdf", size: 10 } });

    await act(async () => button("Retirer")!.click());
    expect(mockedRemove).not.toHaveBeenCalled();
    expect(container.textContent).toContain("Retirer le fichier ?");

    await act(async () => button("Retirer")!.click());
    await flush();
    expect(mockedRemove).toHaveBeenCalledWith(7);
    expect(onChanged).toHaveBeenCalled();
  });

  it("can cancel the removal", async () => {
    render({ file: { fileName: "plan.pdf", mimeType: "image/png", size: 10 } });
    await act(async () => button("Retirer")!.click());
    await act(async () => button("Annuler")!.click());
    expect(container.textContent).not.toContain("Retirer le fichier ?");
    expect(mockedRemove).not.toHaveBeenCalled();
  });

  it("downloads through the authenticated fetch", async () => {
    const createObjectURL = vi.fn(() => "blob:x");
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    mockedFetch.mockClear();
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    mockedFetch.mockResolvedValue(new Blob(["abc"]));
    render({ file: { fileName: "plan.pdf", mimeType: "application/pdf", size: 3 } });
    await flush();

    await act(async () => button("Télécharger")!.click());
    await flush();

    expect(mockedFetch).toHaveBeenCalledWith(7, true);
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:x");
    click.mockRestore();
  });

  it("shows the server's message when the upload fails", async () => {
    mockedUpload.mockRejectedValue(new Error("Fichier trop gros"));
    render();
    const input = container.querySelector<HTMLInputElement>('[data-testid="file-cell-input"]')!;
    await act(async () => {
      Object.defineProperty(input, "files", { value: [new File(["a"], "a.bin")], configurable: true });
      input.dispatchEvent(new Event("change", { bubbles: true }));
    });
    await flush();
    expect(container.textContent).toContain("Fichier trop gros");
  });

  it("offers a preview of an image and not of an archive", () => {
    render({ file: { fileName: "a.png", mimeType: "image/png", size: 1 } });
    expect(button("Agrandir")).toBeDefined();
    render({ file: { fileName: "a.zip", mimeType: "application/zip", size: 1 } });
    expect(button("Agrandir")).toBeUndefined();
  });

  it("shows an image right in the form", async () => {
    render({ file: { fileName: "a.png", mimeType: "image/png", size: 100 } });
    await flush();

    expect(container.querySelector(".file-cell-inline-image img")?.getAttribute("src")).toBe("blob:preview");
    expect(mockedFetch).toHaveBeenCalledWith(7);
  });

  it("shows a PDF right in the form", async () => {
    render({ file: { fileName: "a.pdf", mimeType: "application/pdf", size: 100 } });
    await flush();

    expect(container.querySelector("iframe.file-cell-inline-pdf")?.getAttribute("src")).toBe("blob:preview");
  });

  it("shows no inline preview for a file a browser cannot display, or a very large one", async () => {
    render({ file: { fileName: "a.zip", mimeType: "application/zip", size: 100 } });
    await flush();
    expect(container.querySelector(".file-cell-inline")).toBeNull();

    render({ file: { fileName: "big.png", mimeType: "image/png", size: 50 * 1024 * 1024 } });
    await flush();
    expect(container.querySelector(".file-cell-inline")).toBeNull();
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  it("keeps the file available when its preview cannot be loaded", async () => {
    mockedFetch.mockRejectedValue(new Error("boom"));
    render({ file: { fileName: "a.png", mimeType: "image/png", size: 100 } });
    await flush();

    expect(container.querySelector(".file-cell-inline")).toBeNull();
    expect(button("Télécharger")).toBeDefined();
  });

  it("opens the enlarged view when the inline image is clicked", async () => {
    render({ file: { fileName: "a.png", mimeType: "image/png", size: 100 } });
    await flush();

    await act(async () => container.querySelector<HTMLButtonElement>(".file-cell-inline-image")!.click());
    await flush();

    expect(document.body.querySelector(".p-dialog img")).not.toBeNull();
  });
});
