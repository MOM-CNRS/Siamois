// Every stylesheet the main-panel bundle ships, in load order — imported once by mount.ts (and by
// the dev theme harness, dev/theme/harness.tsx, so it renders with exactly the production CSS).
// No PrimeReact theme (it used to be Lara): every PrimeReact component is skinned by the host
// page's own PrimeFaces theme, primefaces-siamois-theme/theme.css, which aliases its .ui-* rules to
// PrimeReact's .p-* classes (primefaces-themes/theme-base/_primereact.scss). PrimeReact's structural
// CSS needs no import either: each component injects its own at runtime, inside `@layer primereact`
// (primereact/resources/primereact.min.css is an empty, deprecated stub).
import "primeicons/primeicons.css";
// No PrimeFlex either: its global utilities (.grid, .col-N, .hidden, .p-2…) collided with Bootstrap's
// on the host page, and the fiche grid — the only part used — is main-panel.css's own sia-grid/sia-col-N.
import "./main-panel.css";
