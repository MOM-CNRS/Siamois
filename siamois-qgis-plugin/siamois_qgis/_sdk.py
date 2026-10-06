"""Rend `siamois_sdk` importable : paquet installé, dossier `vendor/` (zip du plugin) ou dépôt de dev."""

import os
import sys

try:
    import siamois_sdk  # noqa: F401
except ImportError:
    here = os.path.dirname(os.path.abspath(__file__))
    for candidate in (
        os.path.join(here, "vendor"),
        os.path.join(here, "..", "..", "siamois-sdk-python"),
    ):
        if os.path.isdir(os.path.join(candidate, "siamois_sdk")):
            sys.path.insert(0, os.path.abspath(candidate))
            break
    import siamois_sdk  # noqa: F401,E402
