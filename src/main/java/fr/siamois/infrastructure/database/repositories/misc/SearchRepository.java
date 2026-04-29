package fr.siamois.infrastructure.database.repositories.misc;

import com.zaxxer.hikari.HikariDataSource;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.SearchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private final HikariDataSource dataSource;

    @NonNull
    public List<SearchResultDTO> findResultsFor(@Nullable String input, @NonNull InstitutionDTO institution, @NonNull PersonDTO personDTO) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT bs.* FROM basic_search(?, ?, ?) bs")) {

            statement.setString(1, input);
            statement.setLong(2, institution.getId());
            statement.setLong(3, personDTO.getId());

            log.trace("Executing basic search with params {} {} {}", input, institution.getId(), personDTO.getId());
            return processResultSet(statement);
        } catch (SQLException e) {
            log.error("Error while fetching search results for params {} {} {}", input, institution, personDTO, e);
            return List.of();
        }

    }

    private List<SearchResultDTO> processResultSet(PreparedStatement statement) {
        List<SearchResultDTO> results = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                SearchResultDTO dto = rowToDTO(resultSet);
                results.add(dto);
            }
            return results;
        } catch (SQLException e) {
            log.error("Error while processing search results for params", e);
            return List.of();
        }
    }

    private SearchResultDTO rowToDTO(ResultSet resultSet) {
        try {
            return new SearchResultDTO(
                    resultSet.getString("matching_term"),
                    resultSet.getObject("action_unit_id", Long.class),
                    resultSet.getObject("spatial_unit_id", Long.class),
                    resultSet.getObject("specimen_id", Long.class),
                    resultSet.getLong("similarity_score")
                    );
        } catch (SQLException e) {
            log.error("Error while processing search results for params", e);
            return null;
        }
    }

}
