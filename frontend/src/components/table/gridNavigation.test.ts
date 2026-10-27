import {beforeEach, describe, expect, it} from "vitest";
import {gridTarget} from "./gridNavigation";

// Three rows of three cells; the middle row's last cell and the last row's first have nothing to focus.
function build() {
  document.body.innerHTML = `
    <table><tbody>
      <tr><td><span id="a1" role="button" tabindex="0"></span></td><td><span id="b1" role="button" tabindex="0"></span></td><td><button id="c1"></button></td></tr>
      <tr><td><span id="a2" role="button" tabindex="0"></span></td><td></td><td></td></tr>
      <tr><td></td><td><span id="b3" role="button" tabindex="0"></span></td><td><button id="c3"></button></td></tr>
    </tbody></table>`;
}
const el = (id: string) => document.getElementById(id)!;

describe("gridTarget", () => {
  beforeEach(build);

  it("moves along the row, skipping cells with nothing to focus", () => {
    expect(gridTarget(el("a1"), "ArrowRight")).toBe(el("b1"));
    expect(gridTarget(el("c1"), "ArrowLeft")).toBe(el("b1"));
    expect(gridTarget(el("a2"), "ArrowRight")).toBeNull();
    expect(gridTarget(el("c1"), "ArrowRight")).toBeNull();
  });

  it("goes to the ends of the row", () => {
    expect(gridTarget(el("b1"), "Home")).toBe(el("a1"));
    expect(gridTarget(el("b1"), "End")).toBe(el("c1"));
  });

  it("moves down and up in the same column, passing over rows with nothing there", () => {
    expect(gridTarget(el("b1"), "ArrowDown")).toBe(el("b3"));
    expect(gridTarget(el("b3"), "ArrowUp")).toBe(el("b1"));
    expect(gridTarget(el("a1"), "ArrowDown")).toBe(el("a2"));
    expect(gridTarget(el("a2"), "ArrowDown")).toBeNull();
    expect(gridTarget(el("a1"), "ArrowUp")).toBeNull();
  });

  it("ignores other keys and elements outside the body", () => {
    expect(gridTarget(el("a1"), "a")).toBeNull();
    document.body.innerHTML = `<button id="x"></button>`;
    expect(gridTarget(el("x"), "ArrowDown")).toBeNull();
  });
});
