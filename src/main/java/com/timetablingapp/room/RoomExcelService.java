package com.timetablingapp.room;

import com.timetablingapp.common.excel.ExcelReadException;
import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.room.type.RoomTypeRepository;
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
 * Room template download + upload parsing. Mirrors Laravel RoomExcel.
 * Columns (0-based): A=no, B=room_code, C=name, D=unit_owner, E=location, F=building,
 * G=floor, H=parent(code), I=capacity, J=room_type(name).
 */
@Component
@RequiredArgsConstructor
public class RoomExcelService {

    private final ExcelService excel;
    private final RoomTypeRepository roomTypeRepository;

    private static final List<String> LOCATIONS = List.of("Aceh", "Ciumbuleuit", "Merdeka", "Nias");

    public record RoomRow(String roomCode, String name, String unitOwner, String location,
                          String building, String floor, String parentCode, Integer capacity,
                          String roomTypeName) {
        public boolean isParent() {
            return parentCode == null || parentCode.isBlank();
        }
    }

    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-ruang.xlsx");

        Sheet src = wb.createSheet("Sheet2");
        List<String> types = roomTypeRepository.findAll().stream().map(t -> t.getName()).toList();
        for (int i = 0; i < types.size(); i++) excel.setCell(src, i, 0, types.get(i));       // A
        for (int i = 0; i < LOCATIONS.size(); i++) excel.setCell(src, i, 1, LOCATIONS.get(i)); // B

        Sheet main = wb.getSheetAt(0);
        if (!types.isEmpty())
            excel.addDropdown(main, "'Sheet2'!$A$1:$A$" + types.size(), 1, 300, 9);  // room type -> J
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$" + LOCATIONS.size(), 1, 300, 4);  // location  -> E
        excel.autosize(main, 10);

        return excel.download(wb, "Template Upload Ruang dan Fasilitas.xlsx");
    }

    public List<RoomRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream().map(r -> new RoomRow(
                    at(r, 1), at(r, 2), at(r, 3), at(r, 4), at(r, 5), at(r, 6),
                    at(r, 7), parseInt(at(r, 8)), at(r, 9))).toList();
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
