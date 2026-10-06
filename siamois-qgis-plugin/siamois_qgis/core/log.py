"""Journal : panneau « Messages du journal ▸ SIAMOIS » de QGIS (repli sur `logging` hors QGIS)."""

import logging
import traceback

TAG = "SIAMOIS"
_logger = logging.getLogger("siamois_qgis")

try:
    from qgis.core import Qgis, QgsMessageLog
except ImportError:  # tests hors QGIS
    Qgis = QgsMessageLog = None


def _emit(msg: str, level_name: str) -> None:
    if QgsMessageLog is not None:
        QgsMessageLog.logMessage(msg, TAG, getattr(Qgis, level_name))
    else:
        _logger.log({"Info": logging.INFO, "Warning": logging.WARNING, "Critical": logging.ERROR}[level_name], msg)


def info(msg: str) -> None:
    _emit(msg, "Info")


def warning(msg: str) -> None:
    _emit(msg, "Warning")


def error(msg: str, exc: BaseException = None) -> None:
    if exc is not None:
        msg = f"{msg}\n{''.join(traceback.format_exception(type(exc), exc, exc.__traceback__))}"
    _emit(msg, "Critical")


class QgsLogHandler(logging.Handler):
    """Relaie les logs du SDK (`siamois_sdk`) vers le journal QGIS (niveau DEBUG : requêtes HTTP)."""

    def emit(self, record):
        level = "Critical" if record.levelno >= logging.ERROR else "Warning" if record.levelno >= logging.WARNING else "Info"
        _emit(self.format(record), level)


def attach_sdk_logger() -> QgsLogHandler:
    h = QgsLogHandler()
    h.setFormatter(logging.Formatter("[sdk] %(message)s"))
    lg = logging.getLogger("siamois_sdk")
    lg.setLevel(logging.DEBUG)
    lg.addHandler(h)
    return h


def detach_sdk_logger(h: QgsLogHandler) -> None:
    logging.getLogger("siamois_sdk").removeHandler(h)
