package com.timetablingapp.common.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource-agnostic Apache POI helpers shared by every per-domain Excel component.
 * Mirrors the low-level parts of Laravel's BaseExcel.
 */
@Service
public class ExcelService {

    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String TEMPLATE_DIR = "templates/excel/";

    // ---- reading -------------------------------------------------------------

    /** Open an uploaded .xlsx as a POI workbook. Caller must close it (try-with-resources). */
    public Workbook open(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExcelReadException("No file was uploaded.");
        }
        try {
            return WorkbookFactory.create(file.getInputStream());
        } catch (IOException | RuntimeException e) {
            throw new ExcelReadException("Could not read the uploaded Excel file: " + e.getMessage());
        }
    }

    /**
     * Read the first sheet into rows of trimmed strings, SKIPPING the header row and stopping
     * at the first row whose first cell is blank. Mirrors BaseExcel::toArray() + the
     * `if ($ar[0] == null) break;` loop guard.
     */
    public List<List<String>> readRows(Workbook wb) {
        return readRows(wb, 0, 0);
    }

    /**
     * @param sheetIndex which sheet to read
     * @param keyColumn  0-based column that signals "this row is present" (loop stops when blank)
     */
    public List<List<String>> readRows(Workbook wb, int sheetIndex, int keyColumn) {
        Sheet sheet = wb.getSheetAt(sheetIndex);
        DataFormatter fmt = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        boolean header = true;
        for (Row row : sheet) {
            if (header) {
                header = false;
                continue;
            }
            String key = cellString(row, keyColumn, fmt);
            if (key == null || key.isBlank()) break;
            int last = Math.max(row.getLastCellNum(), keyColumn + 1);
            List<String> cells = new ArrayList<>(last);
            for (int c = 0; c < last; c++) cells.add(cellString(row, c, fmt));
            rows.add(cells);
        }
        return rows;
    }

    /** Raw numeric value of a cell (needed for Excel serial time — see toRoundedLocalTime). */
    public Double numeric(Row row, int col) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        return (cell != null && cell.getCellType() == CellType.NUMERIC) ? cell.getNumericCellValue() : null;
    }

    /** Trimmed string of a cell as the user sees it, or null when the cell is empty. */
    public String cellString(Row row, int col, DataFormatter fmt) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        String v = fmt.formatCellValue(cell).trim();
        return v.isEmpty() ? null : v;
    }

    // ---- Excel serial → LocalTime --------------------------------------------

    /**
     * Convert an Excel time serial (fraction of a day) to LocalTime, rounding to the nearest
     * 30-minute grid exactly like LecturerExcelTime::model():
     *   minute in (0,30]  -> :30
     *   minute in (30,59] -> :00 next hour
     * Returns null when the serial is null.
     */
    public LocalTime toRoundedLocalTime(Double serial) {
        if (serial == null) return null;
        int totalMin = (int) Math.round(serial * 24 * 60);
        int hour = (totalMin / 60) % 24;
        int minute = totalMin % 60;
        if (minute > 0 && minute <= 30) {
            minute = 30;
        } else if (minute > 30) {
            minute = 0;
            hour = (hour + 1) % 24;
        }
        return LocalTime.of(hour, minute);
    }

    /** Convert an Excel time serial to LocalTime without any rounding (used for result times). */
    public LocalTime toLocalTime(Double serial) {
        if (serial == null) return null;
        int totalMin = (int) Math.round(serial * 24 * 60);
        return LocalTime.of((totalMin / 60) % 24, totalMin % 60);
    }

    // ---- writing -------------------------------------------------------------

    public Workbook newWorkbook() {
        return new XSSFWorkbook();
    }

    /** Load a bundled template from classpath (templates/excel/&lt;name&gt;). */
    public Workbook openTemplate(String name) {
        try (InputStream in = new ClassPathResource(TEMPLATE_DIR + name).getInputStream()) {
            return WorkbookFactory.create(in);
        } catch (IOException e) {
            throw new ExcelReadException("Missing Excel template resource: " + name);
        }
    }

    public void writeHeader(Sheet sheet, String... headers) {
        Row row = sheet.getRow(0);
        if (row == null) row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    /** Write a single string value at (rowIdx, colIdx), creating the row/cell as needed. */
    public void setCell(Sheet sheet, int rowIdx, int colIdx, String value) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        row.createCell(colIdx).setCellValue(value == null ? "" : value);
    }

    /**
     * Attach a list-type data-validation dropdown to a column range, sourced from an explicit
     * formula (e.g. "'Sheet2'!$A$1:$A$40"). Mirrors BaseExcel::createDropdown().
     * Guards against an empty source range (a zero-length list would produce an invalid formula).
     */
    public void addDropdown(Sheet sheet, String formula, int firstRow, int lastRow, int col) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);   // XSSF requires true to render the arrow
        validation.setShowPromptBox(true);
        sheet.addValidationData(validation);
    }

    public void autosize(Sheet sheet, int columns) {
        for (int c = 0; c < columns; c++) sheet.autoSizeColumn(c);
    }

    // ---- download ------------------------------------------------------------

    /** Serialize a workbook and wrap it in a ready-to-return download response. Closes the workbook. */
    public ResponseEntity<Resource> download(Workbook wb, String filename) {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            ByteArrayResource body = new ByteArrayResource(out.toByteArray());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(XLSX)
                    .contentLength(body.contentLength())
                    .body(body);
        } catch (IOException e) {
            throw new ExcelReadException("Failed to generate Excel file: " + e.getMessage());
        }
    }
}
