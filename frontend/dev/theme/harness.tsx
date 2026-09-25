// Theme parity harness: every PrimeReact component the main panel uses, next to the PrimeFaces
// markup the JSF app renders for its counterpart, both under the real host stylesheets (see
// harness.html). `?lara=1` also loads the old Lara theme, for a before/after comparison.
import { createRoot } from "react-dom/client";
import { useRef, useState, type ReactNode } from "react";
import "../../src/styles/bundle";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { AutoComplete } from "primereact/autocomplete";
import { Calendar } from "primereact/calendar";
import { MultiSelect } from "primereact/multiselect";
import { Checkbox } from "primereact/checkbox";
import { Panel } from "primereact/panel";
import { Card } from "primereact/card";
import { Chip } from "primereact/chip";
import { Message } from "primereact/message";
import { Toolbar } from "primereact/toolbar";
import { TabView, TabPanel } from "primereact/tabview";
import { DataTable } from "primereact/datatable";
import { Column } from "primereact/column";
import { Menu } from "primereact/menu";
import { BreadCrumb } from "primereact/breadcrumb";
import { ProgressBar } from "primereact/progressbar";
import { Skeleton } from "primereact/skeleton";
import { Dialog } from "primereact/dialog";
import { OverlayPanel } from "primereact/overlaypanel";
import { Tooltip } from "primereact/tooltip";
import { Splitter, SplitterPanel } from "primereact/splitter";

if (new URLSearchParams(location.search).has("lara")) {
  void import("primereact/resources/themes/lara-light-blue/theme.css");
}

const pfButton = (cls: string, icon: string | null, text: string | null, extra = "") =>
  `<button type="button" class="ui-button ui-widget ui-state-default ui-corner-all ${
    icon && text ? "ui-button-text-icon-left" : icon ? "ui-button-icon-only" : "ui-button-text-only"
  } ${cls}" ${extra}>${icon ? `<span class="ui-button-icon-left ui-icon ui-c ${icon}"></span>` : ""}<span class="ui-button-text ui-c">${
    text ?? "ui-button"
  }</span></button>`;

const PF: Record<string, string> = {
  button: [
    pfButton("", "pi pi-check", "Enregistrer"),
    pfButton("ui-state-hover", "pi pi-check", "Hover"),
    pfButton("ui-button-secondary", null, "Secondaire"),
    pfButton("ui-button-outlined", "pi pi-plus", "Outlined"),
    pfButton("ui-button-flat", "pi pi-pencil", "Text"),
    pfButton("rounded-button ui-button-flat", "pi pi-cog", null),
    pfButton("ui-button-danger", "pi pi-trash", "Supprimer"),
    pfButton("ui-state-disabled", null, "Désactivé", "disabled"),
  ].join(""),
  inputtext: `<input class="ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all" placeholder="Texte"/>
    <input class="ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all ui-state-focus" value="Focus"/>
    <input class="ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all ui-state-error" value="Erreur"/>
    <input class="ui-inputfield ui-inputtext ui-widget ui-state-default ui-corner-all ui-state-disabled" value="Désactivé" disabled/>`,
  textarea: `<textarea class="ui-inputfield ui-inputtextarea ui-widget ui-state-default ui-corner-all" rows="3">Zone de texte</textarea>`,
  inputnumber: `<span class="ui-inputnumber ui-widget"><input class="ui-inputfield ui-inputnumber-input ui-widget ui-state-default ui-corner-all" value="12,5"/></span>`,
  autocomplete: `<span class="ui-autocomplete"><input class="ui-autocomplete-input ui-inputfield ui-widget ui-state-default ui-corner-all" placeholder="Rechercher…"/></span>
    <div class="ui-autocomplete ui-autocomplete-multiple"><ul class="ui-autocomplete-multiple-container ui-widget ui-inputfield ui-state-default ui-corner-all">
      <li class="ui-autocomplete-token ui-state-active ui-corner-all"><span class="ui-autocomplete-token-icon ui-icon ui-icon-close"></span><span class="ui-autocomplete-token-label">Néolithique</span></li>
      <li class="ui-autocomplete-input-token"><input type="text"/></li></ul></div>`,
  calendar: `<span class="ui-calendar ui-trigger-calendar"><input class="ui-inputfield ui-widget ui-state-default ui-corner-all" value="25/09/2026"/>${pfButton(
    "ui-datepicker-trigger",
    "ui-icon-calendar pi pi-calendar",
    null,
  )}</span>`,
  multiselect: `<div class="ui-selectcheckboxmenu ui-selectcheckboxmenu-multiple ui-widget ui-state-default ui-corner-all" style="width:14rem">
      <ul class="ui-selectcheckboxmenu-multiple-container ui-widget ui-inputfield ui-state-default ui-corner-all">
        <li class="ui-selectcheckboxmenu-token ui-state-active ui-corner-all"><span class="ui-selectcheckboxmenu-token-label">Fosse</span></li></ul>
      <div class="ui-selectcheckboxmenu-trigger ui-state-default ui-corner-right"><span class="ui-icon ui-icon-triangle-1-s"></span></div></div>`,
  checkbox: `<div class="ui-chkbox ui-widget"><div class="ui-chkbox-box ui-widget ui-corner-all ui-state-default"><span class="ui-chkbox-icon ui-icon ui-icon-blank ui-c"></span></div></div>
    <div class="ui-chkbox ui-widget"><div class="ui-chkbox-box ui-widget ui-corner-all ui-state-default ui-state-active"><span class="ui-chkbox-icon ui-icon ui-icon-check ui-c"></span></div></div>`,
  panel: `<div class="ui-panel ui-widget ui-widget-content ui-corner-all h-block">
      <div class="ui-panel-titlebar ui-widget-header ui-helper-clearfix ui-corner-all"><span class="ui-panel-title">Informations générales</span>
      <a href="#" class="ui-panel-titlebar-icon ui-panel-titlebar-toggler ui-corner-all ui-state-default"><span class="ui-icon pi pi-minus"></span></a></div>
      <div class="ui-panel-content ui-widget-content">Contenu du panneau</div></div>`,
  card: `<div class="ui-card ui-widget ui-widget-content ui-corner-all h-block"><div class="ui-card-body"><div class="ui-card-title">Projets</div><div class="ui-card-content">42 projets</div></div></div>`,
  chip: `<div class="ui-chip"><span class="ui-chip-text">OA-2024</span></div>
    <div class="ui-chip"><span class="ui-chip-icon pi pi-map-marker"></span><span class="ui-chip-text">Lieu</span></div>
    <div class="ui-chip"><span class="ui-chip-text">Retirable</span><span class="ui-chip-remove-icon pi pi-times-circle"></span></div>`,
  message: ["info", "warn", "error"]
    .map(
      (s) =>
        `<div class="ui-message ui-message-${s} ui-widget ui-corner-all"><span class="ui-message-${s}-icon"></span><span class="ui-message-${s}-detail">Message ${s}</span></div>`,
    )
    .join(""),
  toolbar: `<div class="ui-toolbar ui-widget ui-widget-header ui-corner-all h-block" role="toolbar"><div class="ui-toolbar-group-left">${pfButton(
    "",
    "pi pi-plus",
    "Créer",
  )}</div><div class="ui-toolbar-group-right">${pfButton("ui-button-flat", "pi pi-cog", null)}</div></div>`,
  tabview: `<div class="ui-tabs ui-widget ui-widget-content ui-corner-all ui-hidden-container ui-tabs-top h-block">
      <ul class="ui-tabs-nav ui-helper-reset ui-widget-header ui-corner-all" role="tablist">
        <li class="ui-tabs-header ui-state-default ui-tabs-selected ui-state-active ui-corner-top" role="tab"><a href="#">Fiche</a></li>
        <li class="ui-tabs-header ui-state-default ui-corner-top" role="tab"><a href="#">Unités d'enregistrement <span class="ui-badge">3</span></a></li>
        <li class="ui-tabs-header ui-state-default ui-corner-top ui-state-hover" role="tab"><a href="#">Hover</a></li></ul>
      <div class="ui-tabs-panels"><div class="ui-tabs-panel ui-widget-content ui-corner-bottom">Contenu de l'onglet</div></div></div>`,
  datatable: `<div class="ui-datatable ui-widget h-block"><div class="ui-datatable-tablewrapper"><table role="grid">
      <thead><tr>
        <th class="ui-state-default ui-sortable-column ui-state-active" role="columnheader"><span class="ui-column-title">Identifiant</span><span class="ui-sortable-column-icon ui-icon ui-icon-triangle-1-n"></span></th>
        <th class="ui-state-default ui-sortable-column" role="columnheader"><span class="ui-column-title">Type</span><span class="ui-sortable-column-icon ui-icon ui-icon-carat-2-n-s"></span></th>
        <th class="ui-state-default" role="columnheader"><span class="ui-column-title">Date</span></th></tr></thead>
      <tbody class="ui-datatable-data ui-widget-content">
        <tr class="ui-widget-content ui-datatable-even"><td>OA-01</td><td>Fosse</td><td>12/03/2024</td></tr>
        <tr class="ui-widget-content ui-datatable-odd ui-state-hover"><td>OA-02</td><td>Mur</td><td>14/03/2024</td></tr>
        <tr class="ui-widget-content ui-datatable-even ui-state-highlight"><td>OA-03</td><td>Sol</td><td>15/03/2024</td></tr>
      </tbody></table></div></div>`,
  menu: `<div class="ui-menu ui-widget ui-widget-content ui-corner-all ui-helper-clearfix" style="width:12rem"><ul class="ui-menu-list ui-helper-reset">
      <li class="ui-menuitem ui-widget ui-corner-all"><a class="ui-menuitem-link ui-corner-all" href="#"><span class="ui-menuitem-icon ui-icon pi pi-copy"></span><span class="ui-menuitem-text">Dupliquer</span></a></li>
      <li class="ui-menuitem ui-widget ui-corner-all"><a class="ui-menuitem-link ui-corner-all ui-state-hover" href="#"><span class="ui-menuitem-icon ui-icon pi pi-star"></span><span class="ui-menuitem-text">Favori (hover)</span></a></li></ul></div>`,
  breadcrumb: `<nav class="ui-breadcrumb ui-module ui-widget ui-widget-header ui-helper-clearfix ui-corner-all h-block"><ol class="ui-breadcrumb-items">
      <li><a class="ui-menuitem-link" href="#"><span class="ui-menuitem-icon ui-icon pi pi-home"></span></a></li><li class="ui-breadcrumb-chevron pi pi-chevron-right"></li>
      <li><a class="ui-menuitem-link" href="#"><span class="ui-menuitem-text">Projets</span></a></li><li class="ui-breadcrumb-chevron pi pi-chevron-right"></li>
      <li><a class="ui-menuitem-link" href="#"><span class="ui-menuitem-text">OA-2024</span></a></li></ol></nav>`,
  progressbar: `<div class="ui-progressbar ui-widget ui-widget-content ui-corner-all ui-progressbar-determinate h-block" role="progressbar"><div class="ui-progressbar-value ui-widget-header ui-corner-all" style="width:40%;display:block"></div><div class="ui-progressbar-label" style="display:block">40%</div></div>`,
  skeleton: `<div class="ui-skeleton ui-widget" style="width:10rem;height:1rem"></div>`,
  dialog: `<div class="ui-dialog ui-widget ui-widget-content ui-corner-all ui-shadow" style="position:relative;width:24rem;display:block">
      <div class="ui-dialog-titlebar ui-widget-header ui-helper-clearfix ui-corner-top"><span class="ui-dialog-title">Nouvelle unité</span>
      <a href="#" class="ui-dialog-titlebar-icon ui-dialog-titlebar-close ui-corner-all"><span class="ui-icon ui-icon-closethick"></span></a></div>
      <div class="ui-dialog-content ui-widget-content">Contenu du dialogue</div>
      <div class="ui-dialog-footer ui-widget-content">${pfButton("", "pi pi-check", "Créer")}</div></div>`,
  overlaypanel: `<div class="ui-overlaypanel ui-widget ui-widget-content ui-corner-all ui-shadow" style="position:relative;display:block"><div class="ui-overlaypanel-content">Contenu de l'overlay</div></div>`,
  tooltip: `<div class="ui-tooltip ui-widget ui-tooltip-right" style="position:relative;display:block"><div class="ui-tooltip-arrow"></div><div class="ui-tooltip-text ui-shadow ui-corner-all">Info-bulle</div></div>`,
  splitter: `<div class="ui-splitter ui-splitter-horizontal h-block" style="height:4rem"><div class="ui-splitter-panel" style="flex-basis:calc(60% - 4px)">Gauche</div><div class="ui-splitter-gutter"><div class="ui-splitter-gutter-handle"></div></div><div class="ui-splitter-panel" style="flex-basis:calc(40% - 4px)">Droite</div></div>`,
};

const rows = [{ id: "OA-01", type: "Fosse", date: "12/03/2024" }, { id: "OA-02", type: "Mur", date: "14/03/2024" }, { id: "OA-03", type: "Sol", date: "15/03/2024" }];

function OverlayDemo() {
  const op = useRef<OverlayPanel>(null);
  return (
    <>
      <Button label="Ouvrir l'overlay" outlined onClick={(e) => op.current?.toggle(e)} />
      <OverlayPanel ref={op}>Contenu de l'overlay</OverlayPanel>
    </>
  );
}

function DialogDemo() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button label="Ouvrir le dialogue" onClick={() => setOpen(true)} />
      <Dialog header="Nouvelle unité" visible={open} style={{ width: "24rem" }} onHide={() => setOpen(false)}
        footer={<Button icon="pi pi-check" label="Créer" onClick={() => setOpen(false)} />}>
        Contenu du dialogue
      </Dialog>
    </>
  );
}

const REACT: Record<string, ReactNode> = {
  button: (
    <>
      <Button icon="pi pi-check" label="Enregistrer" />
      <Button icon="pi pi-check" label="Hover" />
      <Button label="Secondaire" severity="secondary" />
      <Button icon="pi pi-plus" label="Outlined" outlined />
      <Button icon="pi pi-pencil" label="Text" text />
      <Button icon="pi pi-cog" rounded text />
      <Button icon="pi pi-trash" label="Supprimer" severity="danger" />
      <Button label="Désactivé" disabled />
    </>
  ),
  inputtext: (
    <>
      <InputText placeholder="Texte" />
      <InputText defaultValue="Focus" />
      <InputText defaultValue="Erreur" invalid />
      <InputText defaultValue="Désactivé" disabled />
    </>
  ),
  textarea: <InputTextarea rows={3} defaultValue="Zone de texte" />,
  inputnumber: <InputNumber value={12.5} minFractionDigits={1} locale="fr-FR" />,
  autocomplete: (
    <>
      <AutoComplete placeholder="Rechercher…" suggestions={[]} completeMethod={() => {}} />
      <AutoComplete multiple value={["Néolithique"]} suggestions={[]} completeMethod={() => {}} />
    </>
  ),
  calendar: <Calendar value={new Date(2026, 8, 25)} showIcon dateFormat="dd/mm/yy" />,
  multiselect: <MultiSelect value={["Fosse"]} options={["Fosse", "Mur", "Sol"]} display="chip" style={{ width: "14rem" }} />,
  checkbox: (
    <>
      <Checkbox checked={false} />
      <Checkbox checked />
    </>
  ),
  panel: (
    <Panel header="Informations générales" toggleable className="h-block">
      Contenu du panneau
    </Panel>
  ),
  card: (
    <Card title="Projets" className="h-block">
      42 projets
    </Card>
  ),
  chip: (
    <>
      <Chip label="OA-2024" />
      <Chip label="Lieu" icon="pi pi-map-marker" />
      <Chip label="Retirable" removable />
    </>
  ),
  message: (
    <>
      <Message severity="info" text="Message info" />
      <Message severity="warn" text="Message warn" />
      <Message severity="error" text="Message error" />
    </>
  ),
  toolbar: (
    <Toolbar className="h-block" start={<Button icon="pi pi-plus" label="Créer" />} end={<Button icon="pi pi-cog" text />} />
  ),
  tabview: (
    <TabView className="h-block">
      <TabPanel header="Fiche">Contenu de l'onglet</TabPanel>
      <TabPanel header="Unités d'enregistrement">Deuxième onglet</TabPanel>
      <TabPanel header="Hover">Troisième</TabPanel>
    </TabView>
  ),
  datatable: (
    <DataTable value={rows} className="h-block" sortField="id" sortOrder={1} selectionMode="single" selection={rows[2]} dataKey="id">
      <Column field="id" header="Identifiant" sortable />
      <Column field="type" header="Type" sortable />
      <Column field="date" header="Date" />
    </DataTable>
  ),
  menu: <Menu model={[{ label: "Dupliquer", icon: "pi pi-copy" }, { label: "Favori (hover)", icon: "pi pi-star" }]} style={{ width: "12rem" }} />,
  breadcrumb: <BreadCrumb className="h-block" home={{ icon: "pi pi-home" }} model={[{ label: "Projets" }, { label: "OA-2024" }]} />,
  progressbar: <ProgressBar value={40} className="h-block" />,
  skeleton: <Skeleton width="10rem" height="1rem" />,
  dialog: <DialogDemo />,
  overlaypanel: <OverlayDemo />,
  tooltip: (
    <>
      <Tooltip target=".h-tip" position="right" />
      <Button className="h-tip" label="Survoler" data-pr-tooltip="Info-bulle" outlined />
    </>
  ),
  splitter: (
    <Splitter className="h-block" style={{ height: "4rem" }}>
      <SplitterPanel size={60}>Gauche</SplitterPanel>
      <SplitterPanel size={40}>Droite</SplitterPanel>
    </Splitter>
  ),
};

function Harness() {
  const only = new URLSearchParams(location.search).get("only");
  const keys = Object.keys(PF).filter((k) => !only || only.split(",").includes(k));
  return (
    <div>
      <div className="h-row h-head">
        <div className="h-name">component</div>
        <div>JSF (PrimeFaces markup)</div>
        <div>React (PrimeReact)</div>
      </div>
      {keys.map((k) => (
        <div className="h-row" key={k} id={`row-${k}`}>
          <div className="h-name">{k}</div>
          <div className="h-cell" dangerouslySetInnerHTML={{ __html: PF[k] }} />
          <div className="h-cell">{REACT[k]}</div>
        </div>
      ))}
    </div>
  );
}

createRoot(document.getElementById("harness")!).render(<Harness />);
