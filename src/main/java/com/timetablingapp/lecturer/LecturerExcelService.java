package com.timetablingapp.lecturer;

import com.timetablingapp.common.excel.ExcelReadException;
import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.lecturer.time.LecturerTimeType;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles both lecturer sheets. Mirrors Laravel LecturerExcel + LecturerExcelTime.
 * Lecturer columns: A=no, B=nik, C=name.
 * Lecturer-time columns: A=nik, B=day(name), C=type, D=start(serial), E=end(serial).
 */
@Component
@RequiredArgsConstructor
public class LecturerExcelService {

    private final ExcelService excel;
    private final LecturerRepository lecturerRepository;

    private static final Map<String, Integer> DAYS = Map.of(
            "Senin", 1, "Selasa", 2, "Rabu", 3, "Kamis", 4, "Jumat", 5, "Sabtu", 6);

    public record LecturerRow(String nik, String name) {}

    public record TimeRow(String nik, int day, LecturerTimeType type,
                          LocalTime start, LocalTime end) {}

    // ---- lecturer template (LecturerExcel::generateTemplate is a no-op) -------

    public ResponseEntity<Resource> downloadTemplate() {
        return excel.download(excel.openTemplate("template-dosen.xlsx"), "Template Dosen.xlsx");
    }

    // ---- lecturer-time template ----------------------------------------------

    public ResponseEntity<Resource> downloadTimeTemplate() {
        Workbook wb = excel.openTemplate("template-dosen-time.xlsx");

        Sheet src = wb.createSheet("Sheet2");
        List<String> niks = lecturerRepository.findAllByOrderByNikAsc().stream()
                .map(Lecturer::getNik).toList();
        for (int i = 0; i < niks.size(); i++) excel.setCell(src, i, 0, niks.get(i));
        List<String> days = List.of("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu");
        for (int i = 0; i < days.size(); i++) excel.setCell(src, i, 1, days.get(i));
        excel.setCell(src, 0, 2, "Priority");
        excel.setCell(src, 1, 2, "Not-Available");

        Sheet main = wb.getSheetAt(0);
        if (!niks.isEmpty())
            excel.addDropdown(main, "'Sheet2'!$A$1:$A$" + niks.size(), 1, 300, 0); // A nik
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$6", 1, 300, 1);                 // B day
        excel.addDropdown(main, "'Sheet2'!$C$1:$C$2", 1, 300, 2);                 // C type

        return excel.download(wb, "Template Dosen Time.xlsx");
    }

    // ---- parse ---------------------------------------------------------------

    public List<LecturerRow> parseLecturers(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream()
                    .map(r -> new LecturerRow(at(r, 1), at(r, 2))).toList();
        } catch (IOException e) {
            throw new ExcelReadException(e.getMessage());
        }
    }

    public List<TimeRow> parseTimes(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<TimeRow> out = new ArrayList<>();
            boolean header = true;
            for (Row row : sheet) {
                if (header) {
                    header = false;
                    continue;
                }
                String nik = excel.cellString(row, 0, fmt);
                if (nik == null || nik.isBlank()) break;
                int day = DAYS.getOrDefault(capitalize(excel.cellString(row, 1, fmt)), 1);
                LecturerTimeType type = "Not-Available".equalsIgnoreCase(excel.cellString(row, 2, fmt))
                        ? LecturerTimeType.NOT_AVAILABLE : LecturerTimeType.PRIORITY;
                LocalTime start = excel.toRoundedLocalTime(excel.numeric(row, 3));
                LocalTime end = excel.toRoundedLocalTime(excel.numeric(row, 4));
                out.add(new TimeRow(nik, day, type, start, end));
            }
            return out;
        } catch (IOException e) {
            throw new ExcelReadException(e.getMessage());
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.trim().toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static String at(List<String> r, int i) {
        return (r != null && i < r.size()) ? r.get(i) : null;
    }
}
