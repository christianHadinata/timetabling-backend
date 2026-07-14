package com.timetablingapp.activity;

import com.timetablingapp.activity.constraint.ActivityConstraintRepository;
import com.timetablingapp.activity.constraint.ConstraintType;
import com.timetablingapp.activity.type.ActivityTypeRepository;
import com.timetablingapp.common.excel.ExcelReadException;
import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.common.exception.BadRequestException;
import com.timetablingapp.course.CourseRepository;
import com.timetablingapp.lecturer.Lecturer;
import com.timetablingapp.lecturer.LecturerRepository;
import com.timetablingapp.room.RoomRepository;
import com.timetablingapp.room.type.RoomTypeRepository;
import com.timetablingapp.semester.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Activity template download, all-data export, and upload parsing. Mirrors Laravel ActivitiesExcel.
 * Upload columns (0-based): B(1)=course_code, D(3)=class, E(4)=session, F(5)=duration,
 * G(6)=quota, H(7)=activity_type, I(8)=lecturers, J(9)=room_types, K(10)=room_specifics.
 * Multi-value cells (lecturers / room types / rooms) are pipe-separated.
 */
@Component
@RequiredArgsConstructor
public class ActivityExcelService {

    private final ExcelService excel;
    private final CourseRepository courseRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository roomRepository;
    private final ActivityRepository activityRepository;
    private final ActivityConstraintRepository constraintRepository;
    private final SemesterRepository semesterRepository;

    public record ActivityRow(String courseCode, String courseClass, Integer courseSession,
                              Integer duration, Integer quota, String activityType,
                              List<String> lecturerNiks, List<String> roomTypeNames,
                              List<String> roomCodes) {}

    // ---- template download ---------------------------------------------------

    /**
     * Build the activity upload template. Uses a hidden "Data" sheet as the dropdown source
     * for course code (col B) and activity type (col H). Per phase9.md D4 we ship the robust
     * dropdown-only variant (no VLOOKUP auto-name) — names are re-derived on import.
     */
    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-activity.xlsx");

        Sheet data = wb.createSheet("Data");
        // A=course code, B=course name, C=activity type, D=lecturer nik, E=lecturer name,
        // F=room type, G=room code (row 0 is a header, values from row 1 like the Laravel layout)
        excel.writeHeader(data, "Kode Matkul", "Nama Matkul", "Tipe Aktivitas",
                "NIK Dosen", "Nama Dosen", "Tipe Ruang", "Kode Ruang");

        var courses = courseRepository.findAll();
        for (int i = 0; i < courses.size(); i++) {
            excel.setCell(data, i + 1, 0, courses.get(i).getCode());
            excel.setCell(data, i + 1, 1, courses.get(i).getName());
        }
        var types = activityTypeRepository.findAll();
        for (int i = 0; i < types.size(); i++) excel.setCell(data, i + 1, 2, types.get(i).getName());

        var lecturers = lecturerRepository.findAllByOrderByNikAsc();
        for (int i = 0; i < lecturers.size(); i++) {
            excel.setCell(data, i + 1, 3, lecturers.get(i).getNik());
            excel.setCell(data, i + 1, 4, lecturers.get(i).getName());
        }
        var roomTypes = roomTypeRepository.findAll();
        for (int i = 0; i < roomTypes.size(); i++) excel.setCell(data, i + 1, 5, roomTypes.get(i).getName());
        var rooms = roomRepository.findAll();
        for (int i = 0; i < rooms.size(); i++) excel.setCell(data, i + 1, 6, rooms.get(i).getRoomCode());

        Sheet main = wb.getSheetAt(0);
        if (!courses.isEmpty())
            excel.addDropdown(main, "Data!$A$2:$A$" + (courses.size() + 1), 1, 300, 1); // course code -> B
        if (!types.isEmpty())
            excel.addDropdown(main, "Data!$C$2:$C$" + (types.size() + 1), 1, 300, 7);   // act type    -> H

        return excel.download(wb, "Activity Template.xlsx");
    }

    // ---- all-data export (mirrors ActivitiesExcel::downloadAllAsExcel) --------

    public ResponseEntity<Resource> downloadAll() {
        Integer sem = semesterRepository.findByCurrentTrue()
                .orElseThrow(() -> new BadRequestException("No current semester is set")).getId();
        List<Activity> acts = activityRepository.findBySemester_Id(sem);
        Map<String, String> nameByNik = lecturerRepository.findAll().stream()
                .collect(Collectors.toMap(Lecturer::getNik, Lecturer::getName, (a, b) -> a));

        Workbook wb = excel.openTemplate("empty-template.xlsx");
        Sheet sheet = wb.getSheetAt(0);
        excel.writeHeader(sheet, "Course Code", "Course Name", "Course Class", "Course Session",
                "Duration (jam)", "Quota", "Course Type", "Lecturer");
        int r = 1;
        for (Activity a : acts) {
            String lecturers = constraintRepository.findByActivity_IdAndType(a.getId(), ConstraintType.LECTURER)
                    .stream().map(c -> nameByNik.getOrDefault(c.getValue(), c.getValue()))
                    .collect(Collectors.joining(", "));
            excel.setCell(sheet, r, 0, a.getCourse().getCode());
            excel.setCell(sheet, r, 1, a.getCourse().getName());
            excel.setCell(sheet, r, 2, a.getCourseClass());
            excel.setCell(sheet, r, 3, String.valueOf(a.getCourseSession()));
            excel.setCell(sheet, r, 4, String.valueOf(a.getDuration()));
            excel.setCell(sheet, r, 5, String.valueOf(a.getQuota()));
            excel.setCell(sheet, r, 6, a.getActivityType().getName());
            excel.setCell(sheet, r, 7, lecturers);
            r++;
        }
        excel.autosize(sheet, 8);
        return excel.download(wb, "Activity Data.xlsx");
    }

    // ---- parse ---------------------------------------------------------------

    public List<ActivityRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            // ActivitiesExcel::getFile stops when column B (course_code) is blank -> keyColumn = 1.
            return excel.readRows(wb, 0, 1).stream().map(r -> new ActivityRow(
                    at(r, 1), at(r, 3), parseInt(at(r, 4)), parseInt(at(r, 5)), parseInt(at(r, 6)),
                    at(r, 7), split(at(r, 8)), split(at(r, 9)), split(at(r, 10)))).toList();
        } catch (IOException e) {
            throw new ExcelReadException(e.getMessage());
        }
    }

    static List<String> split(String v) {
        if (v == null || v.isBlank()) return List.of();
        return Arrays.stream(v.split("\\|")).map(String::trim).filter(s -> !s.isBlank()).toList();
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
