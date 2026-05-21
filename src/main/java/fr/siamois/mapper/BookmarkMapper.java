package fr.siamois.mapper;


import fr.siamois.domain.models.Bookmark;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.BookmarkDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import org.jspecify.annotations.Nullable;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.lang.NonNull;

@Mapper(componentModel = "spring")
public interface BookmarkMapper {

    @Mapping(source = "titleCode", target = "title")
    BookmarkDTO toDto(Bookmark bookmark);

    @InheritInverseConfiguration(name="toDto")
    @DelegatingConverter
    Bookmark invertConvert(BookmarkDTO bookmarkDTO);
}