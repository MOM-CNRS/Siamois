import { registerFallbackFieldRenderer, registerFieldRenderer } from "./registry";
import {
  DateRenderer,
  DecimalRenderer,
  FallbackRenderer,
  IntegerRenderer,
  TextRenderer,
} from "./renderers";

// Called once at app startup (from mount.ts) to populate the base renderer set — the scalar
// answerTypes that need no backend lookup (plan §8 phase 2). SELECT_* answerTypes are
// deliberately not registered here yet; unregistered types fall through to FallbackRenderer
// (read-only, shows the raw value) rather than crashing, so a fiche with an unsupported field
// still renders everything else.
let registered = false;

export function registerDefaultFieldRenderers(): void {
  if (registered) return;
  registered = true;

  registerFieldRenderer("TEXT", TextRenderer);
  registerFieldRenderer("INTEGER", IntegerRenderer);
  registerFieldRenderer("DECIMAL", DecimalRenderer);
  registerFieldRenderer("DATETIME", DateRenderer);
  registerFallbackFieldRenderer(FallbackRenderer);
}
