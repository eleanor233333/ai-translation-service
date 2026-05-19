package com.example.aitranslate.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

/**
 * Parses and reconstructs Excel (.xlsx) workbooks for translation.
 *
 * Flow:
 *   1. parseExcelContent()    — extract all unique STRING-type cell values
 *   2. [translation happens externally via TranslateCoreService]
 *   3. mergeExcelContent()    — write translated values back to the original workbook
 *
 * Only STRING-type cells are extracted. NUMERIC, FORMULA, BLANK, and BOOLEAN
 * cells are left unchanged. Deduplication is performed before extraction so
 * repeated cell values are translated only once.
 */
@Component
public class ExcelParseServiceImpl {

    /**
     * Extracts all unique translatable string values from an xlsx workbook.
     * Iterates all sheets and all cells; collects STRING-type cells only.
     *
     * @param xlsxInputStream input stream of the .xlsx file
     * @return deduplicated list of cell string values
     */
    public List<String> parseExcelContent(InputStream xlsxInputStream) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (Workbook workbook = new XSSFWorkbook(xlsxInputStream)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            String text = cell.getStringCellValue().trim();
                            if (!text.isEmpty() && seen.add(text)) {
                                result.add(text);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }

        return result;
    }

    /**
     * Produces a structured command list from an xlsx workbook.
     * Each command records the sheet index, row, column, and cell value
     * so that the translation result can be written back to the exact cell.
     *
     * @param inputStream input stream of the .xlsx file
     * @return JSONArray of cell location + value objects
     */
    public JSONArray parseExcelToCommands(InputStream inputStream) {
        JSONArray commands = new JSONArray();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            int sheetIndex = 0;
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            JSONObject cmd = new JSONObject();
                            cmd.put("sheet_index", sheetIndex);
                            cmd.put("row", row.getRowNum());
                            cmd.put("col", cell.getColumnIndex());
                            cmd.put("value", cell.getStringCellValue());
                            commands.add(cmd);
                        }
                    }
                }
                sheetIndex++;
            }
        } catch (IOException e) {
            return new JSONArray();
        }

        return commands;
    }

    /**
     * Writes translated values back to the original workbook.
     *
     * translatedList contains JSON strings of the form:
     *   { "id": "<original value>", "content": "<translated value>" }
     *
     * Each STRING cell whose value matches an "id" in the translated list
     * is replaced with the corresponding "content". Non-STRING cells and
     * untranslated strings are left unchanged.
     *
     * @param originalContent  input stream of the original .xlsx file
     * @param translatedList   list of translation result JSON strings
     * @return input stream of the modified .xlsx file
     */
    public InputStream mergeExcelContent(InputStream originalContent, List<String> translatedList) {
        // build lookup: original value → translated value
        Map<String, String> resultMap = new HashMap<>();
        for (String jsonStr : translatedList) {
            try {
                JSONObject obj = JSON.parseObject(jsonStr);
                String id      = obj.getString("id");
                String content = obj.getString("content");
                if (id != null && content != null) {
                    resultMap.put(id.trim(), content);
                }
            } catch (Exception ignored) {
            }
        }

        if (resultMap.isEmpty()) {
            return originalContent;
        }

        try (Workbook workbook = new XSSFWorkbook(originalContent)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            String cellValue = cell.getStringCellValue().trim();
                            if (resultMap.containsKey(cellValue)) {
                                cell.setCellValue(resultMap.get(cellValue));
                            }
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            return originalContent;
        }
    }
}
