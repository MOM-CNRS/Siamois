"""Rend `siamois_sdk` importable et vérifie qu'il est assez récent pour ce plugin.

Ordre de recherche : `vendor/` (SDK embarqué dans le zip) > dépôt de dev (`../../siamois-sdk-python`) > SDK installé.
Le SDK embarqué est *préféré* à un `siamois_sdk` déjà présent dans l'environnement Python de QGIS.
"""

import os
import sys

MIN_SDK = (0, 2, 0)  # 0.2.0 : création d'UE/mobilier (cells_to_create, create_*)


def _parse(version):
    parts = []
    for p in str(version).split(".")[:3]:
        digits = "".join(c for c in p if c.isdigit())
        parts.append(int(digits) if digits else 0)
    return tuple(parts + [0] * (3 - len(parts)))


def _use(path):
    loaded = sys.modules.get("siamois_sdk")
    if loaded is not None and os.path.abspath(getattr(loaded, "__file__", "") or "").startswith(path):
        return  # déjà chargé depuis ce dossier : ne pas recréer les classes (identité des exceptions)
    for name in [n for n in sys.modules if n == "siamois_sdk" or n.startswith("siamois_sdk.")]:
        del sys.modules[name]  # ne pas garder un SDK importé d'ailleurs
    sys.path.insert(0, path)


_here = os.path.dirname(os.path.abspath(__file__))
for _candidate in (os.path.join(_here, "vendor"), os.path.join(_here, "..", "..", "siamois-sdk-python")):
    if os.path.isdir(os.path.join(_candidate, "siamois_sdk")):
        _use(os.path.abspath(_candidate))
        break

import siamois_sdk  # noqa: E402,F401

_found = getattr(siamois_sdk, "__version__", "0")
if _parse(_found) < MIN_SDK:
    raise ImportError(
        f"SDK SIAMOIS périmé ({_found} < {'.'.join(map(str, MIN_SDK))}) : {getattr(siamois_sdk, '__file__', '?')}. "
        "Reconstruisez le zip avec ./build.sh (siamois-qgis-plugin) et réinstallez-le, puis redémarrez QGIS.")
