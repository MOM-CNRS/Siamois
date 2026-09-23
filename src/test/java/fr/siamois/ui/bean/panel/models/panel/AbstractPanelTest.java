package fr.siamois.ui.bean.panel.models.panel;

import fr.siamois.dto.view.TableViewState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractPanelTest {

    /**
     * A minimal concrete panel whose {@code isReactPanelEnabled()} is settable per test — the
     * abstract methods AbstractPanel declares are irrelevant to isReactPanelActive()/
     * getPanelContainerId(), so they're stubbed to the simplest legal value.
     */
    private static class TestPanel extends AbstractPanel {
        private final boolean reactEnabled;

        TestPanel(boolean reactEnabled) {
            this.reactEnabled = reactEnabled;
        }

        @Override
        public boolean isReactPanelEnabled() {
            return reactEnabled;
        }

        @Override
        public String getPrefixPanelIndex() {
            return "7";
        }

        @Override
        public String svgIcon() {
            return "";
        }

        @Override
        public String resolveTitleOrTitleCode() {
            return "";
        }

        @Override
        public void refresh() {
        }

        @Override
        public boolean hasPreviousNext() {
            return false;
        }

        @Override
        public String ressourceUri() {
            return "";
        }

        @Override
        public String buildBookmarkUrl() {
            return "";
        }

        @Override
        public void applyViewState(TableViewState state) {
        }

        @Override
        public boolean isDirty() {
            return false;
        }

        @Override
        public boolean isBookmarked() {
            return false;
        }

        @Override
        public void togglePanelBookmark() {
        }

        @Override
        public boolean canUserUpdateView() {
            return false;
        }

        @Override
        public String display() {
            return "";
        }
    }

    @Test
    void isReactPanelActive_falseWhenTheEntityTypeItselfIsNotReactEnabled() {
        TestPanel panel = new TestPanel(false);

        assertThat(panel.isReactPanelActive()).isFalse();
    }

    @Test
    void isReactPanelActive_trueWithNoOverview() {
        TestPanel panel = new TestPanel(true);

        assertThat(panel.isReactPanelActive()).isTrue();
    }

    @Test
    void isReactPanelActive_falseWhenTheOverviewIsStillJsfOnly() {
        // A JSF overview next to a React main panel isn't supported — both fall back to JSF
        // together, matching focus.xhtml's own gate on the react-panel-* block.
        TestPanel panel = new TestPanel(true);
        TestPanel jsfOverview = new TestPanel(false);
        panel.setParentOrOverview(jsfOverview);
        panel.setRoot(true);

        assertThat(panel.isReactPanelActive()).isFalse();
    }

    @Test
    void isReactPanelActive_trueWhenTheOverviewIsAlsoReactEnabled() {
        TestPanel panel = new TestPanel(true);
        TestPanel reactOverview = new TestPanel(true);
        panel.setParentOrOverview(reactOverview);
        panel.setRoot(true);

        assertThat(panel.isReactPanelActive()).isTrue();
    }

    @Test
    void getPanelContainerId_matchesTheReactBlockFocusXhtmlRendersWhenActive() {
        TestPanel panel = new TestPanel(true);

        assertThat(panel.getPanelContainerId()).isEqualTo("react-panel-7");
    }

    @Test
    void getPanelContainerId_matchesTheLegacyBlockFocusXhtmlRendersOtherwise() {
        // Regression: this used to be hardcoded as "panel-" + panelIndex directly in focus.xhtml's
        // p:blockUI, which stayed correct only for this branch — once the React block became the
        // one actually rendered, that hardcoded id pointed at nothing, and PrimeFaces' own
        // MutationObserver (core.js#registerMutationObserver) threw on every DOM mutation
        // thereafter ("Cannot read properties of undefined (reading 'id')").
        TestPanel panel = new TestPanel(false);

        assertThat(panel.getPanelContainerId()).isEqualTo("panel-7");
    }
}
