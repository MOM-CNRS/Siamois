package fr.siamois.utils;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchyUtilsTest {

    /**
     * A hierarchy node, as an entity maps it: its parents and children on both sides.
     */
    private static final class Node {
        private final long id;
        private final Set<Node> parents = new HashSet<>();
        private final Set<Node> children = new HashSet<>();

        /**
         * A node with this id.
         */
        Node(long id) {
            this.id = id;
        }

        /**
         * The node's id.
         */
        Long id() {
            return id;
        }

        /**
         * The node's parents.
         */
        Set<Node> parents() {
            return parents;
        }

        /**
         * The node's children.
         */
        Set<Node> children() {
            return children;
        }

        /**
         * Makes {@code child} a child of this node, on both sides.
         */
        void adopt(Node child) {
            children.add(child);
            child.parents.add(this);
        }
    }

    /**
     * Detaches the node from the parents and children listed, on both sides, and keeps the others.
     */
    @Test
    void detachFromRemovesOnlyTheListedLinksOnBothSides() {
        Node node = new Node(1);
        Node listedParent = new Node(2);
        Node otherParent = new Node(3);
        Node listedChild = new Node(4);
        Node otherChild = new Node(5);
        listedParent.adopt(node);
        otherParent.adopt(node);
        node.adopt(listedChild);
        node.adopt(otherChild);

        HierarchyUtils.detachFrom(node, Node::parents, Node::children, Node::id, Set.of(1L, 2L, 4L));

        assertThat(node.parents()).containsExactly(otherParent);
        assertThat(node.children()).containsExactly(otherChild);
        assertThat(listedParent.children()).isEmpty();
        assertThat(listedChild.parents()).isEmpty();
        assertThat(otherParent.children()).containsExactly(node);
        assertThat(otherChild.parents()).containsExactly(node);
    }

    /**
     * Without ids, nothing changes.
     */
    @Test
    void detachFromNothingKeepsEveryLink() {
        Node node = new Node(1);
        Node parent = new Node(2);
        parent.adopt(node);

        HierarchyUtils.detachFrom(node, Node::parents, Node::children, Node::id, Set.of());

        assertThat(node.parents()).containsExactly(parent);
    }
}
