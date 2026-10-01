import { Component, type ErrorInfo, type ReactNode } from "react";
import { Message } from "primereact/message";
import { t } from "../i18n";

interface Props {
  // What the boundary guards ("le panneau principal", "l'onglet Fouilles"…), for the message.
  label: string;
  // The boundary clears itself when this changes: another entity or tab is another chance.
  resetKey?: string | number;
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Keeps a render failure inside its pane: one broken fiche or tab shows a message and a retry
 * instead of blanking the whole embedded app (React unmounts the entire tree on an uncaught error).
 */
export class PaneErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(`[siamois] ${this.props.label} a planté`, error, info.componentStack);
  }

  componentDidUpdate(prev: Props) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="pane-error" role="alert">
        <Message severity="error" text={t("error.pane", { label: this.props.label })} />
        <button type="button" className="p-button p-button-text" onClick={() => this.setState({ error: null })}>
          {t("error.retry")}
        </button>
      </div>
    );
  }
}
