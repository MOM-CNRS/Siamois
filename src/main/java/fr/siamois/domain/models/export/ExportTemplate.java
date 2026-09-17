package fr.siamois.domain.models.export;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExportTemplate implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String name;
    private String description;
    private List<Sheet> sheets;
    
    public ExportTemplate() {
        this.sheets = new ArrayList<>();
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<Sheet> getSheets() {
        return sheets;
    }
    
    public void setSheets(List<Sheet> sheets) {
        this.sheets = sheets;
    }
    
    public static class Sheet implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String name;
        private List<Source> sources;
        private List<ColumnMapping> columnMappings;
        
        public Sheet() {
            this.sources = new ArrayList<>();
            this.columnMappings = new ArrayList<>();
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public List<Source> getSources() {
            return sources;
        }
        
        public void setSources(List<Source> sources) {
            this.sources = sources;
        }
        
        public List<ColumnMapping> getColumnMappings() {
            return columnMappings;
        }
        
        public void setColumnMappings(List<ColumnMapping> columnMappings) {
            this.columnMappings = columnMappings;
        }
    }
    
    public static class Source implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String table;
        private String types;
        private List<ColumnMapping> mappings;
        
        public Source() {
            this.mappings = new ArrayList<>();
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getTable() {
            return table;
        }
        
        public void setTable(String table) {
            this.table = table;
        }
        
        public String getTypes() {
            return types;
        }
        
        public void setTypes(String types) {
            this.types = types;
        }
        
        public List<ColumnMapping> getMappings() {
            return mappings;
        }
        
        public void setMappings(List<ColumnMapping> mappings) {
            this.mappings = mappings;
        }
    }
    
    public static class ColumnMapping implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String targetColumn;
        private String sourcePath;
        private MappingType type;
        private String constantValue;
        private String sharedField;
        
        public enum MappingType {
            DIRECT, JOINED, CONSTANT, SHARED
        }
        
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public String getTargetColumn() {
            return targetColumn;
        }
        
        public void setTargetColumn(String targetColumn) {
            this.targetColumn = targetColumn;
        }
        
        public String getSourcePath() {
            return sourcePath;
        }
        
        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }
        
        public MappingType getType() {
            return type;
        }
        
        public void setType(MappingType type) {
            this.type = type;
        }
        
        public String getConstantValue() {
            return constantValue;
        }
        
        public void setConstantValue(String constantValue) {
            this.constantValue = constantValue;
        }
        
        public String getSharedField() {
            return sharedField;
        }
        
        public void setSharedField(String sharedField) {
            this.sharedField = sharedField;
        }
    }
}