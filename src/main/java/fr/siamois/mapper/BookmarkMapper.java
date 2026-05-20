package fr.siamois.mapper;


import fr.siamois.domain.models.Bookmark;
import fr.siamois.dto.entity.BookmarkDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookmarkMapper {

    @Mapping(source = "titleCode", target = "title")
    BookmarkDTO toDto(Bookmark bookmark);
}