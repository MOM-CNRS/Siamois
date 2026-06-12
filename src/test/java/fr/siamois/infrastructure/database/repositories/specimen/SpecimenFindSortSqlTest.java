package fr.siamois.infrastructure.database.repositories.specimen;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecimenFindSortSqlTest {

    @Test
    void fromApiSortParam_mapsToNativeOrderBy() {
        assertThat(SpecimenFindSortSql.NativeOrderBy.fromClause(
                SpecimenFindSortSql.fromApiSortParam("fullIdentifier:asc")))
                .isEqualTo(SpecimenFindSortSql.NativeOrderBy.FULL_IDENTIFIER_ASC);

        assertThat(SpecimenFindSortSql.NativeOrderBy.fromClause(
                SpecimenFindSortSql.fromApiSortParam("id:desc")))
                .isEqualTo(SpecimenFindSortSql.NativeOrderBy.SPECIMEN_ID_DESC);
    }

    @Test
    void fromClause_unknownSort_fallsBackToDefault() {
        assertThat(SpecimenFindSortSql.NativeOrderBy.fromClause("s.full_identifier; DROP TABLE specimen"))
                .isEqualTo(SpecimenFindSortSql.NativeOrderBy.DEFAULT);
    }
}
