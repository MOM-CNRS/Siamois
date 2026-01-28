package fr.siamois.utils.stratigraphy.stratifiant;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class Stratifiant {

    private Stratifiant() {
        throw new IllegalStateException("Utility class");
    }

    // Load recording units and their relationship from a spreadsheet
    public static List<RecordingUnit> loadStratifiantDataFromSpreadsheet(String filename) throws IOException {

        ArrayList<RecordingUnit> units = new ArrayList<>();

        try (FileInputStream file = new FileInputStream(filename)) {

            //Create Workbook instance holding reference to .xlsx file
            Map<String, RecordingUnit> indexMap;
            boolean firstRow;
            XSSFSheet sheetRel;
            try (XSSFWorkbook workbook = new XSSFWorkbook(file)) {

                // We start with the nodes
                XSSFSheet sheetUS = workbook.getSheetAt(0);
                int nodeIndex = 0;
                indexMap = new HashMap<>();

                firstRow = true;
                for (Row row : sheetUS) {
                    if (firstRow) {
                        firstRow = false; // Skip the first row
                        continue;
                    }
                    // New unit
                    RecordingUnit unit = new RecordingUnit();
                    unit.setId((long) nodeIndex);
                    String nodeName = row.getCell(0).getStringCellValue();
                    unit.setFullIdentifier(nodeName);
                    indexMap.put(nodeName, unit);
                    units.add(unit);
                    nodeIndex++;
                }

                // Now we add the relationships
                // Now we add edges
                sheetRel = workbook.getSheetAt(1);
            }

            firstRow = true;
            for (Row row : sheetRel) {
                if (firstRow) {
                    firstRow = false; // Skip the first row
                    continue;
                }
                // New rel
                StratigraphicRelationship rel = new StratigraphicRelationship();
                String ru1Name = String.valueOf(row.getCell(0).getStringCellValue());
                String relType = String.valueOf(row.getCell(1).getStringCellValue());
                String ru2Name = String.valueOf(row.getCell(2).getStringCellValue());
                // find node RU1
                rel.setUnit1(indexMap.get(ru1Name));
                rel.setUnit2(indexMap.get(ru2Name));

                if (Objects.equals(relType, "sous") || Objects.equals(relType, "peut-être sous")) {
                    rel.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
                } else if (Objects.equals(relType, "synchrone avec") || Objects.equals(relType, "pt.être synchrone")) {
                    rel.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
                }

                // Add the rel to the unit1
                indexMap.get(ru1Name).getRelationshipsAsUnit1().add(rel);
            }

        }


        return units;

    }
}
