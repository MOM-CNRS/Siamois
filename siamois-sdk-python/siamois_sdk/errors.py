"""Exceptions du SDK. Les messages par défaut sont destinés à l'utilisateur final (FR)."""

from __future__ import annotations

from typing import Any, Optional


class SiamoisError(Exception):
    """Erreur de base. `status` = code HTTP (None si erreur réseau), `payload` = corps JSON éventuel."""

    default_message = "Une erreur est survenue."

    def __init__(self, message: Optional[str] = None, status: Optional[int] = None, payload: Any = None):
        self.status = status
        self.payload = payload
        self.server_message = message
        super().__init__(message or self.default_message)

    @property
    def user_message(self) -> str:
        """Message clair pour l'utilisateur (détail serveur ajouté s'il existe)."""
        if self.server_message and self.server_message != self.default_message:
            return f"{self.default_message} ({self.server_message})"
        return self.default_message


class NetworkError(SiamoisError):
    default_message = "Le serveur ne répond pas. Vérifiez votre connexion internet et l'URL de l'API."


class AuthenticationError(SiamoisError):
    default_message = "Votre session a expiré ou vos identifiants sont invalides. Veuillez vous reconnecter."


class ForbiddenError(SiamoisError):
    default_message = "Vous n'avez pas les droits nécessaires pour cette action."


class NotFoundError(SiamoisError):
    default_message = "L'élément demandé n'existe pas ou n'est pas accessible."


class ValidationError(SiamoisError):
    default_message = "Erreur de validation : les données envoyées sont refusées par le serveur."


class ConflictError(SiamoisError):
    """409. Pour un conflit de révision, `current_revision` et `server_state` sont renseignés."""

    default_message = "Conflit : l'élément a été modifié sur le serveur."

    def __init__(self, message=None, status=409, payload=None):
        super().__init__(message, status, payload)
        data = (payload or {}).get("data") if isinstance(payload, dict) else None
        data = data if isinstance(data, dict) else {}
        self.expected_revision = data.get("expectedRevision")
        self.current_revision = data.get("currentRevision")
        self.server_state = data.get("serverState")

    @property
    def is_revision_conflict(self) -> bool:
        return self.current_revision is not None


class ServerError(SiamoisError):
    default_message = "Une erreur serveur est survenue. Veuillez réessayer plus tard."


class InvalidResponseError(SiamoisError):
    default_message = "Les données reçues du serveur sont invalides. Contactez l'administrateur."


def error_from_response(status: int, payload: Any) -> SiamoisError:
    message = None
    if isinstance(payload, dict):
        message = payload.get("message") or payload.get("detail") or payload.get("error")
    cls = {
        400: ValidationError,
        401: AuthenticationError,
        403: ForbiddenError,
        404: NotFoundError,
        409: ConflictError,
    }.get(status)
    if cls is None:
        cls = ServerError if status >= 500 else SiamoisError
    return cls(message, status, payload)
