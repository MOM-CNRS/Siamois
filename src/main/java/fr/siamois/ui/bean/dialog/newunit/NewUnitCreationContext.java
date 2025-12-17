package fr.siamois.ui.bean.dialog.newunit;


import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewUnitCreationContext<ID> {
    private final NewUnitInsertMode insertMode;
    private final ID clickedId; // id du node cliqué dans le tree
}
