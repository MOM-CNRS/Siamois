package fr.siamois.ui.bean.settings.components;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SortStateTest {

    @Test
    void startsAscendingOnTheDefaultColumn() {
        SortState sort = new SortState("name");

        assertThat(sort.getKey()).isEqualTo("name");
        assertThat(sort.isAscending()).isTrue();
        assertThat(sort.iconOf("name")).isEqualTo("bi-sort-up");
        assertThat(sort.iconOf("members")).isEqualTo("bi-chevron-expand");
    }

    @Test
    void clickingTheSortedColumnFlipsTheDirection() {
        SortState sort = new SortState("name");

        sort.toggle("name");
        assertThat(sort.isAscending()).isFalse();
        assertThat(sort.iconOf("name")).isEqualTo("bi-sort-down");

        sort.toggle("name");
        assertThat(sort.isAscending()).isTrue();
    }

    @Test
    void clickingAnotherColumnSortsItAscending() {
        SortState sort = new SortState("name");
        sort.toggle("name");

        sort.toggle("members");

        assertThat(sort.getKey()).isEqualTo("members");
        assertThat(sort.isAscending()).isTrue();
        assertThat(sort.isSorted("members")).isTrue();
        assertThat(sort.isSorted("name")).isFalse();
    }
}
