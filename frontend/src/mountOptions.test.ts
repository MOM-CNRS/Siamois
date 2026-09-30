import { describe, expect, it, vi } from "vitest";
import { MOUNT_CONTRACT_VERSION, MountContractError, parseMountOptions, type MountDataset } from "./mountOptions";

// What Facelets renders for a project list in focus mode (reactPanelMount.xhtml): a null EL value
// is an empty attribute.
const dataset: MountDataset = {
  contractVersion: String(MOUNT_CONTRACT_VERSION),
  panelIndex: "7",
  panelKind: "detail",
  entityType: "project",
  entityId: "12",
  organizationId: "3",
  overviewEntityType: "recordingUnit",
  overviewEntityId: "40",
  overviewOrganizationId: "",
  writeMode: "true",
  goBackUrl: "",
  basePath: "/siamois",
  locale: "en",
  csrfHeader: "X-CSRF-TOKEN",
  csrfToken: "tok",
  mainResourceUri: "/action-unit/12",
  mainTitle: "Fouille",
  mainBookmarked: "true",
  overviewResourceUri: "/recording-unit/40",
  overviewTitle: "UE 40",
  overviewBookmarked: "false",
  actionSetOverview: "reactAction_setOverview_7",
  actionSetMain: "reactAction_setMain_7",
  overviewActionCloseOverview: "reactAction_overview_closeOverview_7",
  actionRefreshBookmarks: "reactAction_refreshBookmarks_7",
  actionOpenProjectSettings: "reactAction_openProjectSettings_7",
};

const noActions = () => undefined;

describe("parseMountOptions", () => {
  it("reads the mount div's data-* attributes", () => {
    expect(parseMountOptions(dataset, noActions)).toMatchObject({
      panelIndex: "7",
      panelKind: "detail",
      entityType: "project",
      entityId: "12",
      organizationId: 3,
      overviewEntityType: "recordingUnit",
      overviewEntityId: "40",
      overviewOrganizationId: undefined,
      writeMode: true,
      goBackUrl: undefined,
      basePath: "/siamois",
      locale: "en",
      csrf: { headerName: "X-CSRF-TOKEN", token: "tok" },
      main: { resourceUri: "/action-unit/12", title: "Fouille", bookmarked: true },
      overview: { resourceUri: "/recording-unit/40", title: "UE 40", bookmarked: false },
    });
  });

  it("has no overview chrome without an overview resource, and read mode by default", () => {
    const options = parseMountOptions({ ...dataset, overviewResourceUri: "", writeMode: undefined, entityId: "" }, noActions);
    expect(options.overview).toBeUndefined();
    expect(options.writeMode).toBe(false);
    expect(options.entityId).toBeUndefined();
  });

  it("turns the remoteCommands' named params into the bridge's positional arguments", () => {
    const called: Record<string, unknown> = {};
    const fns = Object.fromEntries(
      ["setOverview", "setMain", "closeOverview", "refreshBookmarks", "openProjectSettings"].map((name) => [
        `reactAction_${name}`,
        vi.fn((params) => void (called[name] = params)),
      ]),
    );
    const resolve = (name?: string) => {
      const key = Object.keys(fns).find((k) => name?.startsWith(k));
      return key ? fns[key] : undefined;
    };
    const { bridge } = parseMountOptions(
      {
        ...dataset,
        actionSetOverview: "reactAction_setOverview_7",
        actionSetMain: "reactAction_setMain_7",
        overviewActionCloseOverview: "reactAction_closeOverview_7",
        actionRefreshBookmarks: "reactAction_refreshBookmarks_7",
        actionOpenProjectSettings: "reactAction_openProjectSettings_7",
      },
      resolve,
    );

    bridge!.setOverview!("recordingUnit", 40);
    bridge!.setMain!("/action-unit/12");
    bridge!.openProjectSettings!(12);
    bridge!.closeOverview!();
    expect(called.setOverview).toEqual({ entityType: "recordingUnit", id: 40 });
    expect(called.setMain).toEqual({ path: "/action-unit/12" });
    expect(called.openProjectSettings).toEqual({ projectId: 12 });
    expect(fns.reactAction_closeOverview).toHaveBeenCalled();
  });

  it("leaves out the bridge calls the page didn't define", () => {
    const { bridge } = parseMountOptions(dataset, noActions);
    expect(bridge).toEqual({
      setOverview: undefined,
      setMain: undefined,
      closeOverview: undefined,
      refreshBookmarks: undefined,
      openProjectSettings: undefined,
    });
  });

  it.each([undefined, "0", String(MOUNT_CONTRACT_VERSION + 1)])("refuses a page speaking contract %s", (version) => {
    expect(() => parseMountOptions({ ...dataset, contractVersion: version }, noActions)).toThrow(MountContractError);
  });

  it("refuses an unknown panel kind", () => {
    expect(() => parseMountOptions({ ...dataset, panelKind: "tree" }, noActions)).toThrow(MountContractError);
  });
});
