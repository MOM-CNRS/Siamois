package fr.siamois.domain.services.recordingunit;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RecordingUnitSortFilterService {

    private final RecordingUnitRepository recordingUnitRepository;

    private record FilterBinding(String column, Function<FilterDTO, Specification<RecordingUnit>> toSpec) {}

    private static final List<FilterBinding> USER_FILTERS = List.of(
            text(RecordingUnitSpec.FULL_IDENTIFIER, RecordingUnitSpec::fullIdentifierContains),
            ids(RecordingUnitSpec.AUTHOR_FILTER, RecordingUnitSpec::authorIsIn),
            text(RecordingUnitSpec.MATRIX_FILTER, RecordingUnitSpec::matrixContains),
            ids(RecordingUnitSpec.SPATIAL_UNIT_FILTER, RecordingUnitSpec::isInSpatialUnit),
            ids(RecordingUnitSpec.ACTION_UNIT_FILTER, RecordingUnitSpec::isInActionUnit),
            ids(RecordingUnitSpec.CONTRIBUTORS_FILTER, RecordingUnitSpec::isInContributors),
            concept(RecordingUnitSpec.TYPE_FILTER),
            concept(RecordingUnitSpec.NATURE_FILTER),
            concept(RecordingUnitSpec.AGENT_FILTER),
            concept(RecordingUnitSpec.INTERPRETATION_FILTER),
            dateRange(RecordingUnitSpec.OPENING_DATE_FILTER),
            dateRange(RecordingUnitSpec.CLOSING_DATE_FILTER),
            intRange(RecordingUnitSpec.TPQ_FILTER),
            intRange(RecordingUnitSpec.TAQ_FILTER),
            ids(RecordingUnitSpec.PARENTS_FILTER, RecordingUnitSpec::isChildOf),
            ids(RecordingUnitSpec.CHILDREN_FILTER, RecordingUnitSpec::isParentOf));

    private static FilterBinding text(String column, Function<String, Specification<RecordingUnit>> toSpec) {
        return new FilterBinding(column, filters -> toSpec.apply(filters.valueOfAsString(column)));
    }

    private static FilterBinding ids(String column, Function<List<Long>, Specification<RecordingUnit>> toSpec) {
        return new FilterBinding(column, filters -> toSpec.apply(filters.valueAsIdListOf(column)));
    }

    private static FilterBinding concept(String column) {
        return ids(column, conceptIds -> RecordingUnitSpec.conceptIsIn(column, conceptIds));
    }

    private static FilterBinding dateRange(String column) {
        return new FilterBinding(column, filters -> {
            FilterDTO.DateRange range = filters.valueAsDateRangeOf(column);
            return RecordingUnitSpec.dateFieldBetween(column, range.from(), range.to());
        });
    }

    private static FilterBinding intRange(String column) {
        return new FilterBinding(column, filters -> {
            FilterDTO.IntRange range = filters.valueAsIntRangeOf(column);
            return RecordingUnitSpec.integerFieldBetween(column, range.from(), range.to());
        });
    }

    static Specification<RecordingUnit> userFilterSpecs(@NonNull FilterDTO filters) {
        Specification<RecordingUnit> specification = Specification.where(null);

        for (FilterBinding binding : USER_FILTERS) {
            if (filters.containsColumn(binding.column())) {
                specification = specification.and(binding.toSpec().apply(filters));
            }
        }

        return specification;
    }

    Specification<RecordingUnit> prepareSpecs(@NonNull InstitutionDTO institution, @NonNull FilterDTO filters) {
        Specification<RecordingUnit> base = RecordingUnitSpec.recordingUnitInInstitution(institution.getId());

        if (filters.isRootOnly()) {
            // Scope filters (e.g. "this action unit") are always part of the fixed query context,
            // even when there is no active user search — unlike user filters, they must never be
            // dropped in root-mode, otherwise roots from other contexts leak into the tree.
            Specification<RecordingUnit> scoped = base.and(scopeFilterSpecs(filters));
            if (filters.hasUserFilters()) {
                Collection<Long> closure = resolveAncestorClosure(institution, filters);
                if (closure.isEmpty()) {
                    return scoped.and((root, q, cb) -> cb.disjunction());
                }
                return scoped.and(RecordingUnitSpec.unitIsRoot()).and(RecordingUnitSpec.idIn(closure));
            }
            return scoped.and(RecordingUnitSpec.unitIsRoot());
        }

        return base.and(userFilterSpecs(filters));
    }

    private static Specification<RecordingUnit> scopeFilterSpecs(@NonNull FilterDTO filters) {
        Specification<RecordingUnit> specification = Specification.where(null);
        Set<String> scopeKeys = filters.getScopeFilterKeys();

        for (FilterBinding binding : USER_FILTERS) {
            if (scopeKeys.contains(binding.column())) {
                specification = specification.and(binding.toSpec().apply(filters));
            }
        }

        return specification;
    }

    private Collection<Long> resolveAncestorClosure(InstitutionDTO institution, FilterDTO filters) {
        if (filters.getAncestorClosure() != null) {
            return filters.getAncestorClosure();
        }
        Specification<RecordingUnit> matchSpecs = RecordingUnitSpec
                .recordingUnitInInstitution(institution.getId())
                .and(userFilterSpecs(filters));

        List<Long> matchIds = recordingUnitRepository.findAll(matchSpecs)
                .stream()
                .map(RecordingUnit::getId)
                .toList();
        Set<Long> closure = matchIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(recordingUnitRepository.findAncestorClosure(matchIds.toArray(Long[]::new)));
        filters.setAncestorClosure(closure);
        filters.setMatchIds(new HashSet<>(matchIds));
        return closure;
    }

    Set<Long> computeAncestorClosure(InstitutionDTO institution, FilterDTO filters) {
        if (!filters.isRootOnly() || !filters.hasUserFilters()) {
            return Collections.emptySet();
        }
        return new HashSet<>(resolveAncestorClosure(institution, filters));
    }

    private static final Map<String, Function<Sort.Direction, Specification<RecordingUnit>>> COUNT_SORTS = Map.of(
            RecordingUnitSpec.SPECIMEN_COUNT_SORT, RecordingUnitSpec::orderBySpecimenCount,
            RecordingUnitSpec.RELATIONSHIP_COUNT_SORT, RecordingUnitSpec::orderByRelationshipCount,
            RecordingUnitSpec.PARENTS_COUNT_SORT, RecordingUnitSpec::orderByParentsCount,
            RecordingUnitSpec.CHILDREN_COUNT_SORT, RecordingUnitSpec::orderByChildrenCount);

    private static final Map<String, String> CONCEPT_LABEL_SORTS = Map.of(
            RecordingUnitSpec.NATURE_LABEL_SORT, RecordingUnitSpec.NATURE_FILTER,
            RecordingUnitSpec.AGENT_LABEL_SORT, RecordingUnitSpec.AGENT_FILTER,
            RecordingUnitSpec.INTERPRETATION_LABEL_SORT, RecordingUnitSpec.INTERPRETATION_FILTER);

    private static final String DEFAULT_LANG = "fr";

    Specification<RecordingUnit> applySyntheticSort(Specification<RecordingUnit> specs, Sort sort) {
        for (Sort.Order order : sort) {
            Function<Sort.Direction, Specification<RecordingUnit>> countSort = COUNT_SORTS.get(order.getProperty());
            if (countSort != null) {
                return specs.and(countSort.apply(order.getDirection()));
            }
            String conceptAttribute = CONCEPT_LABEL_SORTS.get(order.getProperty());
            if (conceptAttribute != null) {
                return specs.and(RecordingUnitSpec.orderByConceptLabel(conceptAttribute, currentLang(), order.getDirection()));
            }
        }
        return specs;
    }

    Pageable stripSyntheticSort(Pageable pageable) {
        boolean hasSyntheticSort = pageable.getSort().stream()
                .anyMatch(order -> COUNT_SORTS.containsKey(order.getProperty())
                        || CONCEPT_LABEL_SORTS.containsKey(order.getProperty()));
        if (!hasSyntheticSort) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static String currentLang() {
        UserInfo info = ExecutionContextHolder.get();
        return info != null && info.getLang() != null ? info.getLang() : DEFAULT_LANG;
    }
}
