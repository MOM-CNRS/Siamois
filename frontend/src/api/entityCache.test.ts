import { describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { patchCachedEntity } from "./entityCache";
import { queryKeys } from "./queryKeys";

describe("patchCachedEntity", () => {
  it("patches the entity's rows in its type's lists and its detail, whatever the id's type", () => {
    const client = new QueryClient();
    const page = { data: [{ id: 7, validated: "INCOMPLETE" }, { id: 8, validated: "INCOMPLETE" }], totalCount: 2, limit: 50, offset: 0 };
    client.setQueryData(queryKeys.entityListPage("phase", { offset: 0, limit: 50 }), page);
    client.setQueryData(queryKeys.entityListPage("find", { offset: 0, limit: 50 }), page);
    client.setQueryData(queryKeys.entityDetail("phase", "7"), { id: 7, validated: "INCOMPLETE" });
    client.setQueryData(queryKeys.entityDetail("phase", "8"), { id: 8, validated: "INCOMPLETE" });

    patchCachedEntity(client, "phase", 7, { validated: "VALIDATED" });

    const phases = client.getQueryData<typeof page>(queryKeys.entityListPage("phase", { offset: 0, limit: 50 }))!;
    expect(phases.data.map((r) => r.validated)).toEqual(["VALIDATED", "INCOMPLETE"]);
    expect(client.getQueryData<typeof page>(queryKeys.entityListPage("find", { offset: 0, limit: 50 }))!.data[0].validated).toBe("INCOMPLETE");
    expect(client.getQueryData(queryKeys.entityDetail("phase", "7"))).toEqual({ id: 7, validated: "VALIDATED" });
    expect(client.getQueryData(queryKeys.entityDetail("phase", "8"))).toEqual({ id: 8, validated: "INCOMPLETE" });
  });
});
