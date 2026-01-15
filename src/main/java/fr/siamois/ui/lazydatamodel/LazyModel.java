package fr.siamois.ui.lazydatamodel;

import org.primefaces.model.SortMeta;

import java.util.Set;

public interface LazyModel {

    int getFirst();

    int getPageSizeState();

    Set<SortMeta> getSortBy();

    int getFirstIndexOnPage() ;

    int getLastIndexOnPage();

    int getRowCount();

}
