import { AnchoredFormOverlay } from "../AnchoredFormOverlay";
import { LinkForm, type LinkFormProps } from "./LinkForm";

export type LinkOverlayProps = Omit<LinkFormProps, "onCancel" | "onSubmit"> & {
  // The button the overlay opens next to; null = closed.
  anchor: HTMLElement | null;
  onHide: () => void;
  // Resolves once saved; the overlay then closes itself.
  onSubmit: LinkFormProps["onSubmit"];
};

// A link form in an overlay anchored on the button that opened it.
export function LinkOverlay({ anchor, onHide, onSubmit, ...form }: LinkOverlayProps) {
  return (
    <AnchoredFormOverlay anchor={anchor} ariaLabel={form.title} onHide={onHide}>
      {(close) => <LinkForm {...form} onCancel={close} onSubmit={async (value) => { await onSubmit(value); close(); }} />}
    </AnchoredFormOverlay>
  );
}
