package fr.siamois.ui.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkItem {
    private String id;
    private String title;
    private String uri;
    private String icon;
    private String styleClass;
}