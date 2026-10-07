"""Journal : *Messages du journal ▸ SIAMOIS GIF* (repli sur `logging` hors QGIS)."""

import logging
import traceback

TAG = "SIAMOIS GIF"
_logger = logging.getLogger("layout_gif")

try:
    from qgis.core import Qgis, QgsMessageLog
except ImportError:  # tests hors QGIS
    Qgis = QgsMessageLog = None


def _emit(msg: str, level: str) -> None:
    if QgsMessageLog is not None:
        QgsMessageLog.logMessage(msg, TAG, getattr(Qgis, level))
    else:
        _logger.log({"Info": logging.INFO, "Warning": logging.WARNING, "Critical": logging.ERROR}[level], msg)


def info(msg: str) -> None:
    _emit(msg, "Info")


def warning(msg: str) -> None:
    _emit(msg, "Warning")


def error(msg: str, exc: BaseException = None) -> None:
    if exc is not None:
        msg += "\n" + "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))
    _emit(msg, "Critical")
