package com.timetablingapp.result;

import com.timetablingapp.activity.Activity;
import com.timetablingapp.activity.constraint.ActivityConstraint;
import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.common.excel.ExcelReadException;
import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.jurusan.JurusanService;
import com.timetablingapp.lecturer.Lecturer;
import com.timetablingapp.lecturer.LecturerRepository;
import com.timetablingapp.room.Room;
import com.timetablingapp.room.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Result exports (SIAKAD + printable grid) and result import parsing. Mirrors Laravel ResultsExcel.
 */
@Component
@RequiredArgsConstructor
public class ResultExcelService {

    private final ExcelService excel;
    private final ResultRepository resultRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomRepository roomRepository;
    private final JurusanService jurusanService;
    private final ActivityConstraintRepository constraintRepository;

    private static final String[] DAY = {"Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"};
    private static final Map<String, Integer> IMPORT_DAYS = Map.of(
            "Senin", 1, "Selasa", 2, "Rabu", 3, "Kamis", 4, "Jumat", 5);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** One parsed result row from the upload. */
    public record ResultRow(String courseCode, String courseClass, Integer courseSession,
                            int day, LocalTime start, LocalTime end, String roomCode,
                            Integer quota, String actType, List<String> niks) {}

    // ---- SIAKAD export -------------------------------------------------------

    public ResponseEntity<Resource> exportSiakad(Integer semesterId, String faculty) {
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        List<Result> valid = resultRepository.findForExport(semesterId, true, jurusanIds);
        List<Result> invalid = resultRepository.findForExport(semesterId, false, jurusanIds);
        Map<String, String> nameByNik = lecturerRepository.findAll().stream()
                .collect(Collectors.toMap(Lecturer::getNik, Lecturer::getName, (a, b) -> a));

        Workbook wb = excel.openTemplate("empty-template.xlsx");
        Sheet siakad = wb.getSheetAt(0);
        wb.setSheetName(0, "SIAKAD");
        excel.writeHeader(siakad, "No", "Kode Matkul", "Nama Kelas", "Kelas", "Sesi", "Hari",
                "Waktu Mulai", "Waktu Berakhir", "Ruang", "Jumlah", "Jenis Temu", "NIK", "Pengajar");
        int r = 1;
        for (Result res : valid) {
            Activity a = res.getActivity();
            List<String> niks = lecturerNiks(a.getId());
            excel.setCell(siakad, r, 0, String.valueOf(r));
            excel.setCell(siakad, r, 1, a.getCourse().getCode());
            excel.setCell(siakad, r, 2, a.getCourse().getName());
            excel.setCell(siakad, r, 3, a.getCourseClass());
            excel.setCell(siakad, r, 4, String.valueOf(a.getCourseSession()));
            excel.setCell(siakad, r, 5, dayName(res.getDay()));
            excel.setCell(siakad, r, 6, fmt(res.getStartTime()));
            excel.setCell(siakad, r, 7, fmt(res.getEndTime()));
            excel.setCell(siakad, r, 8, res.getRoom() != null ? res.getRoom().getRoomCode() : "");
            excel.setCell(siakad, r, 9, String.valueOf(a.getQuota()));
            excel.setCell(siakad, r, 10, a.getActivityType().getName());
            excel.setCell(siakad, r, 11, String.join("|", niks));
            excel.setCell(siakad, r, 12, niks.stream()
                    .map(n -> nameByNik.getOrDefault(n, n)).collect(Collectors.joining("|")));
            r++;
        }

        Sheet notInserted = wb.createSheet("Not Inserted");
        excel.writeHeader(notInserted, "No", "Kode Matkul", "Nama Kelas", "Kelas", "Sesi",
                "Jumlah", "Jenis Temu", "NIK", "Pengajar");
        int i = 1;
        for (Result res : invalid) {
            Activity a = res.getActivity();
            List<String> niks = lecturerNiks(a.getId());
            excel.setCell(notInserted, i, 0, String.valueOf(i));
            excel.setCell(notInserted, i, 1, a.getCourse().getCode());
            excel.setCell(notInserted, i, 2, a.getCourse().getName());
            excel.setCell(notInserted, i, 3, a.getCourseClass());
            excel.setCell(notInserted, i, 4, String.valueOf(a.getCourseSession()));
            excel.setCell(notInserted, i, 5, String.valueOf(a.getQuota()));
            excel.setCell(notInserted, i, 6, a.getActivityType().getName());
            excel.setCell(notInserted, i, 7, String.join("|", niks));
            excel.setCell(notInserted, i, 8, niks.stream()
                    .map(n -> nameByNik.getOrDefault(n, n)).collect(Collectors.joining("|")));
            i++;
        }
        excel.autosize(siakad, 13);
        excel.autosize(notInserted, 9);
        return excel.download(wb, "Siakad Export.xlsx");
    }

    // ---- printable grid export -----------------------------------------------

    public ResponseEntity<Resource> exportPrint(Integer semesterId, String faculty) {
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        List<Result> valid = resultRepository.findForExport(semesterId, true, jurusanIds);
        List<Room> rooms = roomRepository.findAll();

        // day -> (roomCode -> results)
        Map<Integer, Map<String, List<Result>>> byDay = new TreeMap<>();
        for (Result res : valid) {
            if (res.getDay() == null || res.getRoom() == null) continue;
            int day = Integer.parseInt(res.getDay());
            byDay.computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(res.getRoom().getRoomCode(), k -> new ArrayList<>()).add(res);
        }

        Workbook wb = excel.newWorkbook();
        for (Map.Entry<Integer, Map<String, List<Result>>> dayEntry : byDay.entrySet()) {
            int day = dayEntry.getKey();
            Map<String, List<Result>> perRoom = dayEntry.getValue();
            Sheet sheet = wb.createSheet(dayName(String.valueOf(day)));

            excel.setCell(sheet, 0, 0, "Jadwal Hari " + dayName(String.valueOf(day)));
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 17));

            String[] header = {"Rooms", "7-8", "8-9", "9-10", "10-11", "11-12", "12-13", "13-14",
                    "14-15", "15-16", "16-17", "17-18", "18-19", "19-20", "20-21", "21-22", "22-23", "23-24"};
            excel.writeHeader(sheet, header);

            int rowIdx = 2;
            for (Room room : rooms) {
                String code = room.getRoomCode();
                excel.setCell(sheet, rowIdx, 0, code);
                List<Result> acts = perRoom.get(code);
                if (acts != null) {
                    for (Result res : acts) {
                        if (res.getStartTime() == null || res.getEndTime() == null) continue;
                        int startH = res.getStartTime().getHour();
                        int endH = res.getEndTime().getHour() == 0 ? 24 : res.getEndTime().getHour();
                        int firstCol = startH - 6;
                        int lastCol = Math.max(firstCol, endH - 7);
                        if (firstCol < 1 || firstCol > 17) continue;
                        lastCol = Math.min(lastCol, 17);

                        Activity a = res.getActivity();
                        String text = a.getCourse().getName() + " (" + a.getCourseClass() + ") - "
                                + a.getActivityType().getName();
                        excel.setCell(sheet, rowIdx, firstCol, text);
                        if (lastCol > firstCol) {
                            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, firstCol, lastCol));
                        }
                        applyColor(wb, sheet, rowIdx, firstCol, a.getCourse().getColor());
                    }
                }
                rowIdx++;
            }
        }
        if (wb.getNumberOfSheets() == 0) wb.createSheet("Kosong");
        return excel.download(wb, "Print.xlsx");
    }

    // ---- parse (upload) ------------------------------------------------------

    public List<ResultRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<ResultRow> out = new ArrayList<>();
            boolean header = true;
            for (Row row : sheet) {
                if (header) {
                    header = false;
                    continue;
                }
                String code = excel.cellString(row, 1, fmt);
                if (code == null || code.isBlank()) break;
                int day = IMPORT_DAYS.getOrDefault(capitalize(excel.cellString(row, 5, fmt)), 1);
                LocalTime start = readTime(row, 6, fmt);
                LocalTime end = readTime(row, 7, fmt);
                List<String> niks = splitNiks(excel.cellString(row, 11, fmt));
                out.add(new ResultRow(
                        code, excel.cellString(row, 3, fmt), parseInt(excel.cellString(row, 4, fmt)),
                        day, start, end, excel.cellString(row, 8, fmt),
                        parseInt(excel.cellString(row, 9, fmt)), excel.cellString(row, 10, fmt), niks));
            }
            return out;
        } catch (IOException e) {
            throw new ExcelReadException(e.getMessage());
        }
    }

    // ---- helpers -------------------------------------------------------------

    private List<String> lecturerNiks(Integer activityId) {
        return constraintRepository.findByActivity_IdAndType(activityId, ConstraintType.LECTURER)
                .stream().map(ActivityConstraint::getValue).toList();
    }

    private String dayName(String day) {
        if (day == null) return "";
        try {
            int d = Integer.parseInt(day.trim());
            return (d >= 0 && d < DAY.length) ? DAY[d] : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String fmt(LocalTime t) {
        return t == null ? "" : t.format(HM);
    }

    /** Read a time cell: numeric → Excel serial, otherwise parse "HH:mm[:ss]". */
    private LocalTime readTime(Row row, int col, DataFormatter fmt) {
        Double serial = excel.numeric(row, col);
        if (serial != null) return excel.toLocalTime(serial);
        String s = excel.cellString(row, col, fmt);
        if (s == null || s.isBlank()) return null;
        try {
            String v = s.trim();
            return LocalTime.parse(v.length() == 5 ? v : v.substring(0, Math.min(5, v.length())));
        } catch (Exception e) {
            return null;
        }
    }

    private void applyColor(Workbook wb, Sheet sheet, int row, int col, String hsl) {
        byte[] rgb = hslToRgb(hsl);
        if (rgb == null) return;
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(rgb, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Cell cell = sheet.getRow(row).getCell(col);
        if (cell != null) cell.setCellStyle(style);
    }

    /** Parse "hsl(h,s%,l%)" → RGB bytes. Returns null when unparseable. */
    static byte[] hslToRgb(String hsl) {
        if (hsl == null || !hsl.startsWith("hsl(")) return null;
        try {
            String body = hsl.substring(4, hsl.indexOf(')'));
            String[] parts = body.split(",");
            double h = Double.parseDouble(parts[0].trim());
            double s = Double.parseDouble(parts[1].trim().replace("%", "")) / 100.0;
            double l = Double.parseDouble(parts[2].trim().replace("%", "")) / 100.0;
            double c = (1 - Math.abs(2 * l - 1)) * s;
            double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
            double m = l - c / 2;
            double rp, gp, bp;
            if (h < 60)       { rp = c; gp = x; bp = 0; }
            else if (h < 120) { rp = x; gp = c; bp = 0; }
            else if (h < 180) { rp = 0; gp = c; bp = x; }
            else if (h < 240) { rp = 0; gp = x; bp = c; }
            else if (h < 300) { rp = x; gp = 0; bp = c; }
            else              { rp = c; gp = 0; bp = x; }
            return new byte[]{
                    (byte) Math.round((rp + m) * 255),
                    (byte) Math.round((gp + m) * 255),
                    (byte) Math.round((bp + m) * 255)};
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> splitNiks(String v) {
        if (v == null || v.isBlank()) return List.of();
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.trim().toLowerCase();
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static Integer parseInt(String s) {
        try {
            return (s == null || s.isBlank()) ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
