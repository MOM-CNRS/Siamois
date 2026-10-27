package fr.siamois.infrastructure.database.repositories.misc;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private final HikariDataSource dataSource;

    /**
     * Recherche sectionnée ({@code global_search}) : au plus {@code limit} lignes par type, chacune portant le
     * total des correspondances de son type.
     *
     * @param projectIds        restreint à ces projets ({@code null} = pas de restriction)
     * @param visibleProjectIds projets visibles de l'appelant ({@code null} = toute l'institution)
     * @param types             types demandés ({@code null} = tous)
     */
    @NonNull
    public List<GlobalSearchRow> searchSections(@NonNull String input, long institutionId, @Nullable List<Long> projectIds,
                                                @Nullable List<Long> visibleProjectIds, @Nullable List<String> types, int limit) {
        String sql = "SELECT gs.* FROM global_search(?, ?, ?, ?, ?, ?) gs";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input);
            statement.setLong(2, institutionId);
            statement.setArray(3, bigintArray(connection, projectIds));
            statement.setArray(4, bigintArray(connection, visibleProjectIds));
            statement.setArray(5, types == null ? null : connection.createArrayOf("varchar", types.toArray()));
            statement.setInt(6, limit);
            List<GlobalSearchRow> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new GlobalSearchRow(rs.getString("entity_type"), rs.getObject("entity_id", Long.class),
                            rs.getString("label"), rs.getString("subtitle"), rs.getObject("project_id", Long.class),
                            rs.getLong("total")));
                }
            }
            return rows;
        } catch (SQLException e) {
            log.error("Error while running global search for '{}'", input, e);
            return List.of();
        }
    }

    /** Ids des projets de l'institution dont l'identifiant ou le nom vaut {@code ref} (sans tenir compte de la casse). */
    @NonNull
    public List<Long> findProjectIdsByRef(@NonNull String ref, long institutionId) {
        String sql = "SELECT action_unit_id FROM action_unit WHERE fk_institution_id = ? "
                + "AND (lower(full_identifier) = lower(?) OR lower(name) = lower(?))";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, institutionId);
            statement.setString(2, ref);
            statement.setString(3, ref);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        } catch (SQLException e) {
            log.error("Error while resolving project reference '{}'", ref, e);
            return List.of();
        }
    }

    private static Array bigintArray(Connection connection, @Nullable List<Long> ids) throws SQLException {
        return ids == null ? null : connection.createArrayOf("bigint", ids.toArray());
    }

}
