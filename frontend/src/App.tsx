import { RecordingUnitOverviewPanel } from "./panel/RecordingUnitOverviewPanel";
import type { PanelActions } from "./panel/panelActions";

export interface AppProps {
  recordingUnitId: number;
  actions?: PanelActions;
  bookmarked: boolean;
}

/** Root component mounted into the JSF overview panel host — see mount.ts. */
export function App({ recordingUnitId, actions, bookmarked }: AppProps) {
  return (
    <RecordingUnitOverviewPanel recordingUnitId={recordingUnitId} actions={actions} bookmarked={bookmarked} />
  );
}
