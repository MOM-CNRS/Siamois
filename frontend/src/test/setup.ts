// jsdom doesn't lay anything out, and leaves out the scrolling APIs that go with layout. PrimeReact's
// VirtualScroller (EntityListPanel's virtual-scrolled DataTable) calls Element#scrollTo on mount and
// on every scrollToIndex — a no-op is the honest stand-in for a box that never has a size.
if (!Element.prototype.scrollTo) {
  Element.prototype.scrollTo = function scrollTo() {};
}

// PrimeReact's VirtualScroller decides how many rows to render from its own box's offsetHeight,
// which jsdom always reports as 0 — so a virtual-scrolled table would render no rows at all under
// test. Give just that box (and nothing else, so no other component's measuring is affected) a
// viewport tall enough for every fixture's handful of rows.
const VIRTUAL_SCROLLER_TEST_SIZE = 800;
for (const prop of ["offsetHeight", "offsetWidth"] as const) {
  const original = Object.getOwnPropertyDescriptor(HTMLElement.prototype, prop);
  Object.defineProperty(HTMLElement.prototype, prop, {
    configurable: true,
    get(this: HTMLElement) {
      if (this.classList?.contains("p-virtualscroller")) return VIRTUAL_SCROLLER_TEST_SIZE;
      return original?.get ? original.get.call(this) : 0;
    },
  });
}

// ...and it only initializes once that same box reports a non-empty bounding rect.
const originalGetBoundingClientRect = Element.prototype.getBoundingClientRect;
Element.prototype.getBoundingClientRect = function getBoundingClientRect(this: Element) {
  if (this.classList?.contains("p-virtualscroller")) {
    const size = VIRTUAL_SCROLLER_TEST_SIZE;
    return { x: 0, y: 0, top: 0, left: 0, right: size, bottom: size, width: size, height: size, toJSON: () => ({}) } as DOMRect;
  }
  return originalGetBoundingClientRect.call(this);
};

// A browser resolves an unset padding to "0px"; jsdom leaves it "". VirtualScroller parseFloat()s
// its content box's paddings to position rows, and NaN there means it renders none — report what a
// browser would.
const PADDINGS = new Set(["paddingTop", "paddingRight", "paddingBottom", "paddingLeft"]);
const originalGetComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = ((elt: Element, pseudo?: string | null) => {
  const style = originalGetComputedStyle(elt, pseudo);
  return new Proxy(style, {
    get(target, prop) {
      const value = Reflect.get(target, prop, target);
      if (typeof prop === "string" && PADDINGS.has(prop) && value === "") return "0px";
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
}) as typeof window.getComputedStyle;
