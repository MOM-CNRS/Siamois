"""« Manger » les vrais points : on les masque temporairement dans le rendu de la couche.

Le rendu d'origine est enveloppé dans une règle parente dont le filtre exclut les entités mangées
(`$id NOT IN (…)`). Les données ne sont jamais modifiées ; `restore()` remet le rendu d'origine.
Si le rendu de la couche ne peut pas être converti (carte de chaleur, etc.), les points restent visibles.
"""

from __future__ import annotations

from typing import Iterable, Optional, Set

from qgis.core import QgsRuleBasedRenderer, QgsVectorLayer


class PointHider:
    def __init__(self, layer: QgsVectorLayer):
        self.layer = layer
        self._original = layer.renderer().clone() if layer.renderer() is not None else None
        self._wrapper: Optional[QgsRuleBasedRenderer.Rule] = None
        self._hidden: Set[int] = set()
        self.active = False

    def start(self) -> bool:
        """Installe le rendu filtrable. Retourne False si la couche ne s'y prête pas."""
        if self._original is None:
            return False
        converted = QgsRuleBasedRenderer.convertFromRenderer(self._original.clone())
        if converted is None:
            return False
        children = converted.rootRule().takeChildren()
        wrapper = QgsRuleBasedRenderer.Rule(None, 0, 0, "", "")
        for child in children:
            wrapper.appendChild(child)
        root = QgsRuleBasedRenderer.Rule(None)
        root.appendChild(wrapper)
        self._wrapper = wrapper
        self.layer.setRenderer(QgsRuleBasedRenderer(root))
        self.active = True
        self._hidden.clear()
        return True

    def hide(self, fids: Iterable[int]) -> None:
        """Masque ces entités (en plus de celles déjà masquées) et redessine la couche."""
        if not self.active:
            return
        new = set(fids) - self._hidden
        if not new:
            return
        self._hidden |= new
        ids = ",".join(str(i) for i in sorted(self._hidden))
        self._wrapper.setFilterExpression(f"$id NOT IN ({ids})")
        self.layer.triggerRepaint()

    def show_all(self) -> None:
        """Rejoue : tous les points réapparaissent, le rendu filtrable reste en place."""
        if self.active and self._hidden:
            self._hidden.clear()
            self._wrapper.setFilterExpression("")
            self.layer.triggerRepaint()

    def restore(self) -> None:
        if self.active and self._original is not None:
            self.layer.setRenderer(self._original)
            self.layer.triggerRepaint()
        self.active = False
        self._wrapper = None
        self._hidden.clear()
