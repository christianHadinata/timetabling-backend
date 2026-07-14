package com.timetablingapp.course;

import com.timetablingapp.common.excel.ExcelReadException;
import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.jurusan.Jurusan;
import com.timetablingapp.jurusan.JurusanRepository;
import com.timetablingapp.jurusan.konsentrasi.KonsentrasiRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Course template download + upload parsing. Mirrors Laravel CourseExcel.
 * Template columns (0-based): A=no, B=code, C=name, D=type, E=tingkat, F=konsentrasi, G=jurusan(name).
 */
@Component
@RequiredArgsConstructor
public class CourseExcelService {

    private final ExcelService excel;
    private final JurusanRepository jurusanRepository;
    private final KonsentrasiRepository konsentrasiRepository;

    /** One parsed course row from the upload. */
    public record CourseRow(String code, String name, String type, Integer tingkat,
                            String konsentrasi, String jurusanName) {}

    // ---- template download (mirrors CourseExcel::generateTemplate) -----------

    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-matkul.xlsx");

        // Hidden "Sheet2" with dropdown sources: A=tingkat(1..8), B=type, C=konsentrasi, D=jurusan
        Sheet src = wb.createSheet("Sheet2");
        for (int i = 1; i <= 8; i++) excel.setCell(src, i - 1, 0, String.valueOf(i));
        excel.setCell(src, 0, 1, "Wajib");
        excel.setCell(src, 1, 1, "Pilihan");

        List<String> kons = konsentrasiRepository.findAll().stream()
                .map(k -> k.getKonsentrasi()).filter(k -> k != null && !k.isBlank())
                .distinct().sorted().toList();
        for (int i = 0; i < kons.size(); i++) excel.setCell(src, i, 2, kons.get(i));

        List<Jurusan> jurusans = jurusanRepository.findAll();
        for (int i = 0; i < jurusans.size(); i++) excel.setCell(src, i, 3, jurusans.get(i).getName());

        Sheet main = wb.getSheetAt(0);
        int last = 300;
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$2", 1, last, 3);                         // type    -> D
        excel.addDropdown(main, "'Sheet2'!$A$1:$A$8", 1, last, 4);                         // tingkat -> E
        if (!kons.isEmpty())
            excel.addDropdown(main, "'Sheet2'!$C$1:$C$" + kons.size(), 1, last, 5);        // kons    -> F
        if (!jurusans.isEmpty())
            excel.addDropdown(main, "'Sheet2'!$D$1:$D$" + jurusans.size(), 1, last, 6);    // jurusan -> G

        return excel.download(wb, "Template Matkul.xlsx");
    }

    // ---- parse upload --------------------------------------------------------

    public List<CourseRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream().map(r -> new CourseRow(
                    at(r, 1), at(r, 2), at(r, 3),
                    parseInt(at(r, 4)), at(r, 5), at(r, 6))).toList();
        } catch (IOException e) {
            throw new ExcelReadException(e.getMessage());
        }
    }

    static String at(List<String> r, int i) {
        return (r != null && i < r.size()) ? r.get(i) : null;
    }

    static Integer parseInt(String s) {
        try {
            return (s == null || s.isBlank()) ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
