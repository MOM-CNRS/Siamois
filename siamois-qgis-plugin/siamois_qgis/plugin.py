"""Plugin QGIS SIAMOIS : connexion, ouverture d'un projet (3 couches GeoPackage), vérification et synchronisation."""

from __future__ import annotations

import os
from typing import Any, Dict, List, Optional, Tuple

from qgis.core import Qgis, QgsApplication, QgsProject, QgsSettings, QgsTask
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import QAction, QApplication, QFileDialog, QMessageBox, QPushButton

from ._sdk import siamois_sdk  # noqa: F401
from siamois_sdk import SiamoisClient
from siamois_sdk.flatten import ColumnSpec
from .core import loader, qgis_layers as ql, syncplan
from .core.store import Store
from .ui.dialogs import LoginDialog, OpenProjectDialog, ReportDialog

TITLE = "SIAMOIS"
SETTINGS = "siamois/"


class _Ctx:
    """Une couche SIAMOIS ouverte, avec ce qu'il faut pour comparer et pousser."""

    def __init__(self, layer, store: Store, name: str, info: Dict[str, Any], current: Dict[str, Any], new_rows: int):
        self.layer, self.store, self.name, self.info = layer, store, name, info
        self.current, self.new_rows = current, new_rows
        self.specs: List[ColumnSpec] = info["columns"]
        self.kind: str = info["kind"]
        self.changes: List[syncplan.RowChange] = []


class SiamoisPlugin:
    def __init__(self, iface):
        self.iface = iface
        self.client: Optional[SiamoisClient] = None
        self.actions: List[QAction] = []
        self._tasks: List[QgsTask] = []
        self.menu = TITLE

    # ------------------------------------------------------------------ cycle de vie

    def initGui(self):
        def add(text, slot):
            a = QAction(text, self.iface.mainWindow())
            a.triggered.connect(slot)
            self.iface.addPluginToMenu(self.menu, a)
            self.iface.addToolBarIcon(a)
            self.actions.append(a)

        add("Ouvrir un projet SIAMOIS…", self.open_project)
        add("Ouvrir la fiche de l'élément sélectionné", self.open_form)
        add("Vérifier mes modifications", self.verify)
        add("Synchroniser avec SIAMOIS", self.sync)

    def unload(self):
        for a in self.actions:
            self.iface.removePluginMenu(self.menu, a)
            self.iface.removeToolBarIcon(a)
        self.actions.clear()

    # ------------------------------------------------------------------ utilitaires

    def _msg(self, text: str, level=Qgis.Info, duration: int = 8, button: Optional[Tuple[str, Any]] = None):
        item = self.iface.messageBar().createMessage(TITLE, text)
        if button:
            b = QPushButton(button[0])
            b.clicked.connect(button[1])
            item.layout().addWidget(b)
        self.iface.messageBar().pushWidget(item, level, duration)

    def _run(self, description: str, func, on_done):
        """Exécute `func(task)` hors du fil principal ; `on_done(result, error)` revient dans le fil principal."""
        def finished(exception, value=None):
            self._tasks = [t for t in self._tasks if t is not task]
            on_done(value, exception)

        task = QgsTask.fromFunction(description, func, on_finished=finished)
        self._tasks.append(task)
        QgsApplication.taskManager().addTask(task)

    def _ensure_client(self, url: Optional[str] = None) -> Optional[SiamoisClient]:
        if self.client is not None and self.client.is_authenticated and (not url or self.client.base_url == url.rstrip("/")):
            return self.client
        s = QgsSettings()
        dlg = LoginDialog(self.iface.mainWindow(), url or s.value(SETTINGS + "url", ""), s.value(SETTINGS + "email", ""))
        if url:
            dlg.url.setReadOnly(True)
        if dlg.exec_() != LoginDialog.Accepted:
            return None
        self.client = dlg.client
        s.setValue(SETTINGS + "url", self.client.base_url)
        s.setValue(SETTINGS + "email", dlg.email.text().strip())
        return self.client

    # ------------------------------------------------------------------ ouverture d'un projet

    def open_project(self):
        client = self._ensure_client()
        if client is None:
            return
        dlg = OpenProjectDialog(client, self.iface.mainWindow())
        if dlg.exec_() != OpenProjectDialog.Accepted:
            return
        org_id, project_id = dlg.selection()
        if org_id is None or project_id is None:
            return
        s = QgsSettings()
        start = s.value(SETTINGS + "dir", QgsProject.instance().homePath() or os.path.expanduser("~"))
        folder = QFileDialog.getExistingDirectory(self.iface.mainWindow(), "Dossier de travail (GeoPackage local)", start)
        if not folder:
            return
        s.setValue(SETTINGS + "dir", folder)
        gpkg = os.path.join(folder, f"siamois_{project_id}.gpkg")
        db = gpkg + ".siamois.db"
        if not self._confirm_overwrite(db):
            return

        def work(task):
            def progress(label, done, total):
                if total:
                    task.setProgress(100.0 * done / total)
                return not task.isCanceled()
            return loader.load_project(client, org_id, project_id, progress)

        self._run("SIAMOIS – chargement du projet", work,
                  lambda bundle, err: self._loaded(bundle, err, gpkg, db))

    def _confirm_overwrite(self, db: str) -> bool:
        """Recharger écrase le GeoPackage : prévenir s'il reste des modifications non synchronisées."""
        layers = [l for l in ql.siamois_layers() if l.customProperty(ql.PROP_DB) == db]
        if not layers:
            return True
        ctxs, _ = self._collect(layers, quiet=True)
        pending = sum(1 for c in ctxs for ch in c.changes if ch.has_changes)
        if not pending:
            return True
        r = QMessageBox.question(
            self.iface.mainWindow(), TITLE,
            f"{pending} modification(s) locale(s) non synchronisée(s) seront perdues si vous rechargez le projet.\n"
            "Recharger quand même ?")
        return r == QMessageBox.Yes

    def _loaded(self, bundle, err, gpkg: str, db: str):
        if err is not None or bundle is None:
            if isinstance(err, loader.Cancelled) or err is None:
                self._msg("Chargement annulé.", Qgis.Warning)
            else:
                text = getattr(err, "user_message", str(err))
                self._msg(f"Échec du chargement : {text}", Qgis.Critical, 0)
            return
        try:
            QApplication.setOverrideCursor(Qt.WaitCursor)
            ql.remove_project_layers(bundle.project_name)
            ql.write_geopackage(bundle, gpkg)
            if os.path.exists(db):
                os.remove(db)
            store = Store(db)
            store.set_meta("base_url", bundle.base_url)
            store.set_meta("org_id", bundle.org_id)
            store.set_meta("project_id", bundle.project_id)
            store.set_meta("project_name", bundle.project_name)
            vocabs = {loader.VOCAB_STATUS: loader.status_vocabulary()}
            for code, concepts in bundle.vocab_concepts.items():
                store.save_vocab(code, concepts)
            from siamois_sdk.flatten import Vocabulary
            vocabs.update({c: Vocabulary(v) for c, v in bundle.vocab_concepts.items()})
            layers = ql.add_to_project(bundle, gpkg, db, vocabs)
            for ld, layer in zip(bundle.layers, layers):
                store.save_layer(ld.name, ld.kind, ld.srid, ld.specs)
                wkts = {r.id: ql.norm_wkt(self._wkt(r.geom)) for r in ld.rows}
                store.save_base(ld.name, [{"id": r.id, "cells": r.cells, "geom_wkt": wkts[r.id], "revision": r.revision,
                                           "incomplete": r.incomplete, "allowed": r.allowed} for r in ld.rows])
                layer.afterCommitChanges.connect(self._on_commit)
            store.close()
        except Exception as exc:  # noqa: BLE001 - tout échec d'écriture doit être montré
            self._msg(f"Échec de la création des couches : {exc}", Qgis.Critical, 0)
            return
        finally:
            QApplication.restoreOverrideCursor()
        n = {ld.kind: len(ld.rows) for ld in bundle.layers}
        self._msg(f"Projet « {bundle.project_name} » chargé : {n[loader.KIND_RU]} unité(s) d'enregistrement, "
                  f"{n[loader.KIND_FIND]} mobilier(s). Fichier : {gpkg}", Qgis.Success, 10)

    @staticmethod
    def _wkt(geojson):
        from .core.geo import geojson_to_wkt
        return geojson_to_wkt(geojson)

    def _on_commit(self):
        self._msg("Modifications enregistrées localement.", Qgis.Info, 6, ("Synchroniser maintenant", self.sync))

    # ------------------------------------------------------------------ fiche

    def open_form(self):
        layer = self.iface.activeLayer()
        if layer is None or not layer.customProperty(ql.PROP_DB):
            self._msg("Sélectionnez une couche SIAMOIS puis un élément.", Qgis.Warning)
            return
        feats = layer.selectedFeatures()
        if not feats:
            self._msg("Sélectionnez un élément de la couche (outil de sélection ou table d'attributs).", Qgis.Warning)
            return
        self.iface.openFeatureForm(layer, feats[0], True)

    # ------------------------------------------------------------------ vérification / synchronisation

    def _collect(self, layers, quiet: bool = False) -> Tuple[List[_Ctx], int]:
        ctxs: List[_Ctx] = []
        stores: Dict[str, Store] = {}
        new_rows = 0
        for layer in layers:
            db = layer.customProperty(ql.PROP_DB)
            if not os.path.exists(db):
                continue
            store = stores.setdefault(db, Store(db))
            name = layer.customProperty(ql.PROP_NAME)
            info = store.layer(name)
            if info is None:
                continue
            current, new = ql.read_current(layer, info["columns"])
            new_rows += new
            c = _Ctx(layer, store, name, info, current, new)
            vocabs = store.vocabularies()
            vocabs[loader.VOCAB_STATUS] = loader.status_vocabulary()
            c.vocabs = vocabs
            c.changes = syncplan.collect_changes(name, c.kind, c.specs, store.base(name), current, vocabs,
                                                 geom_editable=c.kind != loader.KIND_FIND)
            ctxs.append(c)
        return ctxs, new_rows

    def _commit_edits(self, layers) -> bool:
        for l in layers:
            if l.isEditable() and l.isModified():
                r = QMessageBox.question(self.iface.mainWindow(), TITLE,
                                         f"Enregistrer les modifications en cours de « {l.name()} » ?",
                                         QMessageBox.Yes | QMessageBox.No | QMessageBox.Cancel)
                if r == QMessageBox.Cancel:
                    return False
                if r == QMessageBox.Yes and not l.commitChanges():
                    self._msg(f"Enregistrement impossible : {'; '.join(l.commitErrors())}", Qgis.Critical, 0)
                    return False
        return True

    @staticmethod
    def _report_rows(ctxs: List[_Ctx]) -> List[Dict]:
        rows = []
        for c in ctxs:
            for ch in c.changes:
                if not ch.issues and ch.has_changes:
                    rows.append({"layer": c.layer.name(), "label": ch.label, "column": "modifié",
                                 "message": "prêt à être envoyé", "level": "ok"})
                for i in ch.issues:
                    rows.append({"layer": c.layer.name(), "label": ch.label, "column": i.column,
                                 "message": i.message, "level": "error" if i.blocking else "warn"})
            if c.new_rows:
                rows.append({"layer": c.layer.name(), "label": f"{c.new_rows} ligne(s) créée(s) dans QGIS",
                             "column": "", "level": "warn",
                             "message": "la création d'éléments n'est pas gérée en v1 : ignorée"})
        return rows

    def verify(self):
        layers = ql.siamois_layers()
        if not layers:
            self._msg("Aucune couche SIAMOIS dans le projet.", Qgis.Warning)
            return
        if not self._commit_edits(layers):
            return
        ctxs, _ = self._collect(layers)
        rows = self._report_rows(ctxs)
        n = sum(1 for c in ctxs for ch in c.changes if ch.has_changes)
        errors = sum(1 for r in rows if r["level"] == "error")
        ReportDialog("SIAMOIS – Vérification", f"{n} élément(s) modifié(s), {errors} erreur(s) bloquante(s).",
                     rows, self.iface.mainWindow()).exec_()

    def sync(self):
        layers = ql.siamois_layers()
        if not layers:
            self._msg("Aucune couche SIAMOIS dans le projet.", Qgis.Warning)
            return
        if not self._commit_edits(layers):
            return
        ctxs, _ = self._collect(layers)
        todo = [(c, ch) for c in ctxs for ch in c.changes if ch.has_changes]
        if not todo:
            self._msg("Rien à synchroniser : aucune modification locale.", Qgis.Info)
            return
        blocked = [(c, ch) for c, ch in todo if ch.blocking]
        if blocked:
            rows = self._report_rows(ctxs)
            dlg = ReportDialog("SIAMOIS – Erreurs de validation",
                               f"{len(blocked)} élément(s) en erreur ne seront pas envoyés. Corrigez-les, ou "
                               f"synchronisez uniquement les {len(todo) - len(blocked)} autre(s).",
                               rows, self.iface.mainWindow(),
                               apply_label="Synchroniser les éléments valides" if len(todo) > len(blocked) else None)
            if dlg.exec_() != ReportDialog.Accepted:
                return
            todo = [(c, ch) for c, ch in todo if not ch.blocking]
        base_url = ctxs[0].store.get_meta("base_url")
        client = self._ensure_client(base_url)
        if client is None:
            return

        def work(task):
            out = []
            for i, (c, ch) in enumerate(todo):
                if task.isCanceled():
                    break
                out.append((c, syncplan.push(client, ch)))
                task.setProgress(100.0 * (i + 1) / len(todo))
            return out

        self._run("SIAMOIS – synchronisation", work, lambda res, err: self._synced(res, err))

    def _synced(self, results, err):
        if err is not None or results is None:
            self._msg(f"Échec de la synchronisation : {getattr(err, 'user_message', err)}", Qgis.Critical, 0)
            return
        ok = errors = 0
        conflicts: List[Tuple[_Ctx, syncplan.PushResult]] = []
        report: List[Dict] = []
        for c, r in results:
            if r.status == "ok":
                ok += 1
                self._mark_synced(c, r.change, r.new_revision)
            elif r.status == "conflict":
                conflicts.append((c, r))
                report.append({"layer": c.layer.name(), "label": r.change.label, "key": f"{c.name}:{r.change.id}",
                               "message": f"modifié sur le serveur (révision {r.conflict.current_revision}, "
                                          f"la vôtre : {r.change.expected_revision})", "level": "conflict"})
            else:
                errors += 1
                if r.status == "auth":
                    self.client = None
                report.append({"layer": c.layer.name(), "label": r.change.label, "column": "envoi",
                               "message": r.message, "level": "error"})
        if conflicts or errors:
            dlg = ReportDialog(
                "SIAMOIS – Synchronisation",
                f"{ok} élément(s) envoyé(s), {len(conflicts)} conflit(s), {errors} erreur(s).",
                report, self.iface.mainWindow(), apply_label="Appliquer mes choix" if conflicts else None)
            if dlg.exec_() == ReportDialog.Accepted and conflicts:
                resolved = self._resolve(conflicts, dlg.decisions())
                self._msg(f"{ok} envoyé(s), {resolved} conflit(s) résolu(s), {errors} erreur(s).",
                          Qgis.Warning if errors else Qgis.Success, 10)
            return
        self._msg(f"✅ Synchronisation terminée : {ok} élément(s) envoyé(s), 0 erreur.", Qgis.Success, 8)

    def _mark_synced(self, c: _Ctx, ch: syncplan.RowChange, new_revision: Optional[int]) -> None:
        rev = new_revision if new_revision is not None else ch.expected_revision
        c.store.update_base(c.name, ch.id, ch.current_cells, ch.current_wkt, rev)
        ql.update_after_sync(c.layer, c.current[ch.id]["fid"], new_revision)

    def _resolve(self, conflicts, decisions: Dict[str, str]) -> int:
        n = 0
        QApplication.setOverrideCursor(Qt.WaitCursor)
        try:
            for c, r in conflicts:
                ch = r.change
                choice = decisions.get(f"{c.name}:{ch.id}", "server")
                if choice == "local":
                    res = syncplan.push(self.client, ch, force_revision=r.conflict.current_revision)
                    if res.status == "ok":
                        self._mark_synced(c, ch, res.new_revision)
                        n += 1
                else:
                    srv = syncplan.fetch_server_row(self.client, c.kind, ch.id, c.specs, c.vocabs)
                    from .core.geo import geojson_to_wkt
                    wkt = ql.norm_wkt(geojson_to_wkt(srv["geom"]))
                    ql.apply_server_row(c.layer, c.current[ch.id]["fid"], c.specs, srv["cells"], wkt, srv["revision"])
                    c.store.update_base(c.name, ch.id, srv["cells"], wkt, srv["revision"])
                    n += 1
        except Exception as exc:  # noqa: BLE001
            self._msg(f"Résolution interrompue : {getattr(exc, 'user_message', exc)}", Qgis.Critical, 0)
        finally:
            QApplication.restoreOverrideCursor()
        return n
