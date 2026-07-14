# Phase 9 — Excel Import / Export

> **Reference Roadmap:** [migration-roadmap.md](migration-roadmap.md) → Phase 9
> **Goal:** Port every Excel import/export feature from the Laravel `App\Http\Controllers\Excel\*` classes and their host controllers into the Spring Boot backend using Apache POI.
> **Depends on:** Phases 3–6 (`course/`, `room/`, `lecturer/`, `activity/`, `result/`) — all present.
> **Status:** Ready for execution.

---

## 0. Prerequisites & Do-First Checklist

Green light to implement. This is a rewrite-first pass: build the structure now, resolve the legacy business rules with the lecturer later. Every genuine unknown is isolated to a single patch point (see §12), so none of them block coding.

**Hard prerequisite (do before the template-download endpoints will work):**
- [ ] Copy the 6 template files from `timetabling_laravel/_project_data/` into `src/main/resources/templates/excel/`, renamed per §2:
      `template matkul.xlsx → template-matkul.xlsx`, `Template Upload Ruang dan Fasilitas.xlsx → template-ruang.xlsx`, `template dosen.xlsx → template-dosen.xlsx`, `Template dosen time.xlsx → template-dosen-time.xlsx`, `Template activity.xlsx → template-activity.xlsx`, `Empty Template.xlsx → empty-template.xlsx`.
      *(Missing files surface as a 500 / `ExcelReadException` at download time — easy to forget.)*
- [ ] Add the multipart limits + `app.import.default-home-base` to `application.properties` (§10).

**Suggested build order** (simplest first, so the `ExcelService` core is proven before the intricate parts):
`Course → Room → Lecturer → Activity → Result-SIAKAD → Result-print (last)`.

**Not blockers, just be aware:**
- The DB has never been connected yet, so `ddl-auto=validate` across all phases is still unverified. Phase 9 adds **zero tables**, so this is not a Phase 9 issue — but expect a mechanical round of column/type fixes the first time the real DB is wired. Watch for a validate failure on `lecturers.home_base` specifically (see D2).
- Legacy rules to confirm with the lecturer later (all have safe defaults shipped): `home_base` (D2), `CourseType` label mapping (§4.2), activity template VLOOKUP (D4), result print-grid coloring (D5).

---

## 1. Overview

Laravel implements Excel via PhpSpreadsheet with a `BaseExcel` abstract class and one subclass per resource (`CourseExcel`, `RoomExcel`, `LecturerExcel`, `LecturerExcelTime`, `ActivitiesExcel`, `ResultsExcel`). Each subclass does three things:

1. **`generateTemplate()`** — loads a pre-built `.xlsx` template from `_project_data/`, appends a hidden helper sheet with dropdown source data (jurusan, room types, lecturers, …), wires data-validation dropdowns / VLOOKUP formulas, and streams the file to the browser.
2. **`model(array $row)`** — maps one spreadsheet row → a plain object with normalized fields.
3. **Export methods** (`downloadAllAsExcel`, `ResultsExcel::download` / `downloadPrint`) — write DB data into a workbook and stream it.

The host controllers (`CourseController@uploadExcel`, etc.) own the **import business rules**: duplicate checks, FK existence checks, transactional entity creation, and a success/failure log.

### Design decision — file layout

The roadmap sketches "1 file + modifications". In practice a single `ExcelService` cannot cleanly hold five resources' worth of template-building and row-mapping logic without becoming a 1,000-line god class. We therefore split responsibilities:

| Layer | Responsibility | Files |
|-------|----------------|-------|
| **Core POI utility** | Low-level, resource-agnostic helpers: read sheet → `List<List<String>>`, load a classpath template, add dropdown validation, autosize, Excel-serial→`LocalTime`, build a downloadable `ByteArrayResource` with headers. | `common/excel/ExcelService.java` |
| **Per-domain Excel component** | One Spring `@Component` per resource that mirrors the Laravel `*Excel` subclass: builds that resource's template + maps its rows + builds its exports. Injects `ExcelService` + the repositories it needs. | `course/CourseExcelService.java`, `room/RoomExcelService.java`, `lecturer/LecturerExcelService.java`, `activity/ActivityExcelService.java`, `result/ResultExcelService.java` |
| **Import orchestration** | Import business rules (duplicate/FK checks, transactional persistence, log building) live on the existing `*Service` classes as new `importXxx(MultipartFile)` methods — they already hold the repositories and `@Transactional` plumbing. | edits to `CourseService`, `RoomService`, `LecturerService`, `ActivityService`, `ResultService` |
| **HTTP** | New `@GetMapping`/`@PostMapping` endpoints on the existing controllers. | edits to 5 controllers |

This keeps CRUD services readable and mirrors the Laravel one-class-per-resource structure.

### Dependencies

`org.apache.poi:poi-ooxml:5.3.0` is **already declared** in [build.gradle.kts](../build.gradle.kts) line 43. No build change required.

---

## 2. Files to Add / Edit / Delete

### Add (10 new Java files + 6 resource files)

```
src/main/java/com/timetablingapp/
├── common/excel/
│   ├── ExcelService.java              # core POI utility
│   ├── ExcelReadException.java        # thrown on malformed upload → 400
│   └── ImportLog.java                 # {filename, succeeded[], failed[]} + Entry{id,message}
├── course/
│   └── CourseExcelService.java
├── room/
│   └── RoomExcelService.java
├── lecturer/
│   └── LecturerExcelService.java      # handles BOTH lecturer + lecturer-time sheets
├── activity/
│   └── ActivityExcelService.java
└── result/
    └── ResultExcelService.java

src/main/resources/templates/excel/     # copied verbatim from timetabling_laravel/_project_data/
├── template-matkul.xlsx                # ← "template matkul.xlsx"
├── template-ruang.xlsx                 # ← "Template Upload Ruang dan Fasilitas.xlsx"
├── template-dosen.xlsx                 # ← "template dosen.xlsx"
├── template-dosen-time.xlsx            # ← "Template dosen time.xlsx"
├── template-activity.xlsx              # ← "Template activity.xlsx"
└── empty-template.xlsx                 # ← "Empty Template.xlsx" (base for exports)
```

### Edit (existing files)

| File | Change |
|------|--------|
| `common/dto/ImportResultResponse.java` | Optionally enrich to carry the structured `ImportLog` (see §3.3). If we keep it as-is, controllers return `ImportLog` directly. **Recommendation:** return `ImportLog` from imports; leave `ImportResultResponse` for callers that only need a flat message. |
| `course/CourseController.java` | Add `GET /export`, `POST /import`. Remove the "Phase 9" TODO comment. |
| `course/CourseService.java` | Add `importCourses(MultipartFile)`. |
| `room/RoomController.java` | Add `GET /export`, `POST /import`. |
| `room/RoomService.java` | Add `importRooms(MultipartFile)` (two-pass parent/child). |
| `lecturer/LecturerController.java` | Add `GET /export`, `GET /export-time`, `POST /import`, `POST /import-time`. |
| `lecturer/LecturerService.java` | Add `importLecturers(...)`, `importLecturerTimes(...)`. |
| `activity/ActivityController.java` | Add `GET /export` (template), `GET /export-all` (data), `POST /import`. |
| `activity/ActivityService.java` | Add `importActivities(MultipartFile)`. |
| `result/ResultController.java` | **Replace** the `exportStub` method with real `GET /export-siakad/{semesterId}`, `GET /export-print/{semesterId}`; add `POST /import`. |
| `result/ResultService.java` | Add `importResults(MultipartFile)`. |
| `config/SecurityConfig.java` | No change required (all endpoints already `authenticated()`); confirm multipart is enabled (Spring Boot default). |

### Delete

None. Phase 9 is purely additive.

---

## 3. Core Utility Layer

### 3.1 `common/excel/ExcelService.java`

Resource-agnostic POI helpers. Everything else builds on this.

```java
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

@Service
public class ExcelService {

    public static final MediaType XLSX =
        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String TEMPLATE_DIR = "templates/excel/";

    // ---- reading -------------------------------------------------------------

    /** Open an uploaded .xlsx as a POI workbook. Caller must try-with-resources it. */
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
     * Read the first sheet into rows of trimmed strings, SKIPPING the header row.
     * Mirrors BaseExcel::toArray() (which does array_shift on the header).
     * Iterates until the FIRST cell of a row is blank — matches the Laravel
     * `if ($ar[0] == null) break;` loop guard.
     */
    public List<List<String>> readRows(Workbook wb) {
        return readRows(wb, 0, 0);
    }

    /**
     * @param sheetIndex which sheet
     * @param keyColumn  0-based column that acts as the "row is present" sentinel
     */
    public List<List<String>> readRows(Workbook wb, int sheetIndex, int keyColumn) {
        Sheet sheet = wb.getSheetAt(sheetIndex);
        DataFormatter fmt = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        boolean header = true;
        for (Row row : sheet) {
            if (header) { header = false; continue; }     // skip header
            String key = cellString(row, keyColumn, fmt);
            if (key == null || key.isBlank()) break;        // stop at first empty key cell
            int last = Math.max(row.getLastCellNum(), keyColumn + 1);
            List<String> cells = new ArrayList<>(last);
            for (int c = 0; c < last; c++) cells.add(cellString(row, c, fmt));
            rows.add(cells);
        }
        return rows;
    }

    /** Raw numeric value of a cell (needed for Excel serial time — see toLocalTime). */
    public Double numeric(Row row, int col) {
        Cell cell = row.getCell(col);
        return (cell != null && cell.getCellType() == CellType.NUMERIC) ? cell.getNumericCellValue() : null;
    }

    private String cellString(Row row, int col, DataFormatter fmt) {
        if (row == null) return null;
        Cell cell = row.getCell(col);
        return cell == null ? null : fmt.formatCellValue(cell).trim();
    }

    // ---- Excel serial → LocalTime --------------------------------------------

    /**
     * Convert an Excel time serial (fraction of a day) to LocalTime, then ROUND to the
     * nearest 30-min grid exactly like LecturerExcelTime::model():
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

    // ---- writing -------------------------------------------------------------

    public Workbook newWorkbook() { return new XSSFWorkbook(); }

    /** Load a bundled template from classpath (templates/excel/<name>). */
    public Workbook openTemplate(String name) {
        try (InputStream in = new ClassPathResource(TEMPLATE_DIR + name).getInputStream()) {
            return WorkbookFactory.create(in);
        } catch (IOException e) {
            throw new ExcelReadException("Missing Excel template resource: " + name);
        }
    }

    public void writeHeader(Sheet sheet, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(headers[i]);
    }

    /** Write a single string value at (rowIdx, colIdx), creating the row/cell as needed. */
    public void setCell(Sheet sheet, int rowIdx, int colIdx, String value) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        row.createCell(colIdx).setCellValue(value == null ? "" : value);
    }

    /**
     * Attach a list-type data-validation dropdown to a column range, sourced from an
     * explicit formula (e.g. "'Sheet2'!$A$1:$A$40"). Mirrors BaseExcel::createDropdown().
     */
    public void addDropdown(Sheet sheet, String formula, int firstRow, int lastRow, int col) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setSuppressDropDownArrow(true);   // XSSF requires true to show the arrow
        validation.setShowPromptBox(true);
        sheet.addValidationData(validation);
    }

    public void autosize(Sheet sheet, int columns) {
        for (int c = 0; c < columns; c++) sheet.autoSizeColumn(c);
    }

    // ---- download ------------------------------------------------------------

    /** Serialize a workbook and wrap it in a ready-to-return download response. */
    public ResponseEntity<Resource> download(Workbook wb, String filename) {
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            ByteArrayResource body = new ByteArrayResource(out.toByteArray());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
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
```

**Notes**
- `DataFormatter.formatCellValue` renders every cell to the string the user sees, so integer quotas come through as `"120"` not `"120.0"` — matches PhpSpreadsheet's `getValue()` for text/number cells.
- Time cells are the exception: we need the raw serial for the 30-minute rounding, hence the separate `numeric()` + `toRoundedLocalTime()` path (only lecturer-time and result imports use it).
- POI's `XSSFWorkbook` is `Closeable`; `download()` closes it via try-with-resources.

### 3.2 `common/excel/ExcelReadException.java`

Maps malformed uploads to HTTP 400 through the existing `GlobalExceptionHandler`.

```java
package com.timetablingapp.common.excel;

public class ExcelReadException extends RuntimeException {
    public ExcelReadException(String message) { super(message); }
}
```

Add a handler in `common/exception/GlobalExceptionHandler.java` (or let it fall through to the generic 400 for `BadRequestException` by having `ExcelReadException extends BadRequestException` instead — **recommended**, so no handler edit is needed):

```java
// Recommended: reuse the existing 400 path.
public class ExcelReadException extends com.timetablingapp.common.exception.BadRequestException {
    public ExcelReadException(String message) { super(message); }
}
```

### 3.3 `common/excel/ImportLog.java`

Structured import result mirroring Laravel's `generateLog($failed, $succeed, $desc)` payload (`{filename, failed:[{id,message}], succeed:[{id,message}]}`).

```java
package com.timetablingapp.common.excel;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ImportLog {

    public record Entry(String id, String message) {}

    private final String filename;
    private final List<Entry> succeeded = new ArrayList<>();
    private final List<Entry> failed = new ArrayList<>();

    public ImportLog(String description) {
        this.filename = description + "_log-"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".txt";
    }

    public void ok(String id)               { succeeded.add(new Entry(id, "")); }
    public void fail(String id, String msg) { failed.add(new Entry(id, msg)); }

    /** Legacy `msg` flag: "1" when everything succeeded, "0" when anything failed. */
    public String msg() { return failed.isEmpty() ? "1" : "0"; }

    public int importedCount() { return succeeded.size(); }
}
```

Controllers return a small response wrapper:

```java
// inline record in each controller, or add to common/dto
public record ImportResponse(String msg, ImportLog log) {}
```

---

## 4. Course Excel

### 4.1 `course/CourseExcelService.java`

Mirrors `CourseExcel`. Template columns (0-based): `A=no, B=code, C=name, D=type, E=tingkat, F=konsentrasi, G=jurusan(name)`.

```java
package com.timetablingapp.course;

import com.timetablingapp.common.excel.ExcelService;
import com.timetablingapp.jurusan.Jurusan;
import com.timetablingapp.jurusan.JurusanRepository;
import com.timetablingapp.jurusan.konsentrasi.KonsentrasiRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CourseExcelService {

    private final ExcelService excel;
    private final JurusanRepository jurusanRepository;
    private final KonsentrasiRepository konsentrasiRepository;

    /** Row model — one parsed course row from the upload. */
    public record CourseRow(String code, String name, String type, Integer tingkat,
                            String konsentrasi, String jurusanName) {}

    // ---- template download (mirrors CourseExcel::generateTemplate) -----------
    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-matkul.xlsx");

        // Build hidden "Sheet2" with dropdown sources: A=tingkat(1..8), B=type, C=konsentrasi, D=jurusan
        Sheet src = wb.createSheet("Sheet2");
        for (int i = 1; i <= 8; i++) excel.setCell(src, i - 1, 0, String.valueOf(i));
        excel.setCell(src, 0, 1, "Wajib");
        excel.setCell(src, 1, 1, "Pilihan");

        List<String> kons = konsentrasiRepository.findAll().stream()
                .map(k -> k.getKonsentrasi()).distinct().sorted().toList();
        for (int i = 0; i < kons.size(); i++) excel.setCell(src, i, 2, kons.get(i));

        List<Jurusan> jurusans = jurusanRepository.findAll();
        for (int i = 0; i < jurusans.size(); i++) excel.setCell(src, i, 3, jurusans.get(i).getName());

        Sheet main = wb.getSheetAt(0);
        int last = 300;
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$2",                       1, last, 3); // type   -> D
        excel.addDropdown(main, "'Sheet2'!$A$1:$A$8",                       1, last, 4); // tingkat-> E
        excel.addDropdown(main, "'Sheet2'!$C$1:$C$" + kons.size(),          1, last, 5); // kons   -> F
        excel.addDropdown(main, "'Sheet2'!$D$1:$D$" + jurusans.size(),      1, last, 6); // jurusan-> G

        return excel.download(wb, "Template Matkul.xlsx");
    }

    // ---- parse upload --------------------------------------------------------
    public List<CourseRow> parse(org.springframework.web.multipart.MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream().map(r -> new CourseRow(
                    at(r, 1), at(r, 2), at(r, 3),
                    parseInt(at(r, 4)), at(r, 5), at(r, 6))).toList();
        } catch (java.io.IOException e) {
            throw new com.timetablingapp.common.excel.ExcelReadException(e.getMessage());
        }
    }

    private static String at(List<String> r, int i) { return i < r.size() ? r.get(i) : null; }
    private static Integer parseInt(String s) {
        try { return (s == null || s.isBlank()) ? null : Integer.valueOf(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
```

### 4.2 `course/CourseService.java` — add `importCourses`

Mirrors `CourseController@uploadExcel`: skip duplicate `code`; validate konsentrasi belongs to the jurusan (or jurusan has no konsentrasi); create otherwise. Jurusan is resolved by **name** (the sheet stores the name).

```java
// new fields
private final CourseExcelService courseExcelService;
private final KonsentrasiRepository konsentrasiRepository;   // if not already present

@Transactional
public ImportLog importCourses(MultipartFile file) {
    ImportLog log = new ImportLog("course");

    // name -> jurusan (matches CourseExcel::model hashJurusan)
    Map<String, Jurusan> byName = jurusanRepository.findAll().stream()
            .collect(Collectors.toMap(Jurusan::getName, j -> j, (a, b) -> a));

    for (CourseExcelService.CourseRow row : courseExcelService.parse(file)) {
        String code = row.code() == null ? "" : row.code().trim();
        if (code.isBlank()) continue;

        if (courseRepository.existsByCode(code)) {
            log.fail(code, "Ditemukan duplikat dengan course code yang sama pada database.");
            continue;
        }
        Jurusan jurusan = byName.get(row.jurusanName());
        if (jurusan == null) {
            log.fail(code, "Jurusan tidak ditemukan: " + row.jurusanName());
            continue;
        }
        List<String> jurKons = konsentrasiRepository.findByJurusanId(jurusan.getId())
                .stream().map(k -> k.getKonsentrasi()).toList();
        boolean konsentrasiOk = jurKons.isEmpty() || jurKons.contains(row.konsentrasi());
        if (!konsentrasiOk) {
            log.fail(code, "Ada kesalahan pada pengaturan konsentrasi jurusan.");
            continue;
        }
        try {
            Course c = new Course();
            c.setCode(code);
            c.setName(row.name());
            c.setType(CourseType.fromLabel(row.type()));   // "Wajib"/"Pilihan" -> enum (see note)
            c.setTingkat(row.tingkat());
            c.setKonsentrasi(row.konsentrasi());
            c.setJurusan(jurusan);
            courseRepository.save(c);
            log.ok(code);
        } catch (Exception e) {
            log.fail(code, "Exception: " + e.getMessage());
        }
    }
    validateLockService.lock();
    return log;
}
```

> **Note — `CourseType` label mapping.** The template stores `"Wajib"`/`"Pilihan"`. Confirm `CourseType` can parse those. If it currently only has `WAJIB`/`PILIHAN`, add a static `fromLabel(String)` that upper-cases and matches, defaulting to `WAJIB`.

### 4.3 `course/CourseController.java` — add endpoints

```java
private final CourseExcelService courseExcelService;   // add to constructor deps

@GetMapping("/export")
public ResponseEntity<Resource> export() {
    return courseExcelService.downloadTemplate();
}

@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(courseService.importCourses(file));
}
```

> The Laravel route is `GET excel-course` / `POST uploads-course`. We normalize to REST-style `/api/courses/export` and `/api/courses/import` per the roadmap's endpoint table.

---

## 5. Room Excel

### 5.1 `room/RoomExcelService.java`

Mirrors `RoomExcel`. Columns: `A=no, B=room_code, C=name, D=unit_owner, E=location, F=building, G=floor, H=parent(code), I=capacity, J=room_type(name)`.

Key behaviours:
- Template dropdowns: `J` (room type, from `RoomType`), `E` (location: Aceh/Ciumbuleuit/Merdeka/Nias — hard-coded like Laravel).
- Parse resolves `room_type` name → id (default to first/`1` when unknown) and `parent` code → id (null when blank/unknown).

```java
@Component
@RequiredArgsConstructor
public class RoomExcelService {
    private final ExcelService excel;
    private final RoomTypeRepository roomTypeRepository;

    private static final List<String> LOCATIONS = List.of("Aceh", "Ciumbuleuit", "Merdeka", "Nias");

    public record RoomRow(String roomCode, String name, String unitOwner, String location,
                          String building, String floor, String parentCode, Integer capacity,
                          String roomTypeName) {
        public boolean isParent() { return parentCode == null || parentCode.isBlank(); }
    }

    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-ruang.xlsx");
        Sheet src = wb.createSheet("Sheet2");
        List<String> types = roomTypeRepository.findAll().stream().map(t -> t.getName()).toList();
        for (int i = 0; i < types.size(); i++)      excel.setCell(src, i, 0, types.get(i));      // A
        for (int i = 0; i < LOCATIONS.size(); i++)  excel.setCell(src, i, 1, LOCATIONS.get(i));  // B
        Sheet main = wb.getSheetAt(0);
        excel.addDropdown(main, "'Sheet2'!$A$1:$A$" + types.size(),     1, 300, 9); // room type -> J
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$" + LOCATIONS.size(), 1, 300, 4); // location  -> E
        excel.autosize(main, 10);
        return excel.download(wb, "Template Upload Ruang dan Fasilitas.xlsx");
    }

    public List<RoomRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream().map(r -> new RoomRow(
                at(r,1), at(r,2), at(r,3), at(r,4), at(r,5), at(r,6),
                at(r,7), parseInt(at(r,8)), at(r,9))).toList();
        } catch (IOException e) { throw new ExcelReadException(e.getMessage()); }
    }
    // at()/parseInt() as in CourseExcelService
}
```

### 5.2 `room/RoomService.java` — add `importRooms` (two-pass)

Parents must be inserted before children so the child's `parent_room_id` FK resolves — Laravel achieves this with `getRoomsFromFile($file, true)` then `(…, false)`. Reuse a single parsed list and split it.

```java
@Transactional
public ImportLog importRooms(MultipartFile file) {
    ImportLog log = new ImportLog("room");
    List<RoomRow> rows = roomExcelService.parse(file);

    Map<String, RoomType> typeByName = roomTypeRepository.findAll().stream()
            .collect(Collectors.toMap(RoomType::getName, t -> t, (a, b) -> a));
    RoomType defaultType = roomTypeRepository.findAll().stream().findFirst().orElse(null);

    // Pass 1 — parents (no parentCode). Pass 2 — children.
    for (boolean parentPass : new boolean[]{true, false}) {
        for (RoomRow row : rows) {
            if (row.isParent() != parentPass) continue;
            String code = row.roomCode();
            if (code == null || code.isBlank()) continue;
            try {
                Room room = new Room();
                room.setRoomCode(code);
                room.setName(row.name());
                room.setUnitOwner(row.unitOwner());
                room.setLocation(row.location());
                room.setBuilding(row.building());
                room.setFloor(row.floor());
                room.setCapacity(row.capacity());
                room.setRoomType(typeByName.getOrDefault(row.roomTypeName(), defaultType));
                if (!row.isParent()) {
                    roomRepository.findByRoomCode(row.parentCode())
                            .ifPresent(room::setParentRoom);   // unknown parent -> null (matches Laravel)
                }
                roomRepository.save(room);
                log.ok(code);
            } catch (Exception e) {
                log.fail(code, "Exception: " + e.getMessage());
            }
        }
    }
    validateLockService.lock();
    return log;
}
```

> Laravel's `RoomExcel::model` does **not** dedupe by `room_code`. We keep parity (no duplicate guard). If duplicate protection is desired, add `if (roomRepository.existsByRoomCode(code)) { log.fail(...); continue; }` — flag this decision for review.

### 5.3 `room/RoomController.java`

```java
@GetMapping("/export")
public ResponseEntity<Resource> export() { return roomExcelService.downloadTemplate(); }

@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(service.importRooms(file));
}
```

---

## 6. Lecturer Excel (lecturer + lecturer-time)

### 6.1 `lecturer/LecturerExcelService.java`

Combines `LecturerExcel` (simple `A=no, B=nik, C=name`) and `LecturerExcelTime`.

**Lecturer-time columns:** `A=nik, B=day(name), C=type, D=start(serial), E=end(serial)`.
- day: Senin=1 … Sabtu=6 (default 1).
- type: `"Priority"`/`"Not-Available"` case-insensitive (default Priority).
- start/end: Excel serial → `toRoundedLocalTime`.

```java
@Component
@RequiredArgsConstructor
public class LecturerExcelService {
    private final ExcelService excel;
    private final LecturerRepository lecturerRepository;

    private static final Map<String,Integer> DAYS = Map.of(
        "Senin",1,"Selasa",2,"Rabu",3,"Kamis",4,"Jumat",5,"Sabtu",6);

    public record LecturerRow(String nik, String name) {}
    public record TimeRow(String nik, int day, LecturerTimeType type,
                          LocalTime start, LocalTime end) {}

    // --- lecturer template: no helper sheet (LecturerExcel::generateTemplate is a no-op) ---
    public ResponseEntity<Resource> downloadTemplate() {
        return excel.download(excel.openTemplate("template-dosen.xlsx"), "Template Dosen.xlsx");
    }

    // --- lecturer-time template: helper sheet A=nik, B=day, C=type + dropdowns on A/B/C ---
    public ResponseEntity<Resource> downloadTimeTemplate() {
        Workbook wb = excel.openTemplate("template-dosen-time.xlsx");
        Sheet src = wb.createSheet("Sheet2");
        List<String> niks = lecturerRepository.findAllByOrderByNikAsc().stream().map(Lecturer::getNik).toList();
        for (int i = 0; i < niks.size(); i++) excel.setCell(src, i, 0, niks.get(i));
        List<String> days = List.of("Senin","Selasa","Rabu","Kamis","Jumat","Sabtu");
        for (int i = 0; i < days.size(); i++) excel.setCell(src, i, 1, days.get(i));
        excel.setCell(src, 0, 2, "Priority");
        excel.setCell(src, 1, 2, "Not-Available");
        Sheet main = wb.getSheetAt(0);
        excel.addDropdown(main, "'Sheet2'!$A$1:$A$" + niks.size(), 1, 300, 0); // A nik
        excel.addDropdown(main, "'Sheet2'!$B$1:$B$6",             1, 300, 1); // B day
        excel.addDropdown(main, "'Sheet2'!$C$1:$C$2",             1, 300, 2); // C type
        return excel.download(wb, "Template Dosen Time.xlsx");
    }

    public List<LecturerRow> parseLecturers(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            return excel.readRows(wb).stream()
                .map(r -> new LecturerRow(at(r,1), at(r,2))).toList();
        } catch (IOException e) { throw new ExcelReadException(e.getMessage()); }
    }

    public List<TimeRow> parseTimes(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            List<TimeRow> out = new ArrayList<>();
            boolean header = true;
            for (Row row : sheet) {
                if (header) { header = false; continue; }
                String nik = str(row, 0, fmt);
                if (nik == null || nik.isBlank()) break;
                int day = DAYS.getOrDefault(capitalize(str(row,1,fmt)), 1);
                LecturerTimeType type = "Not-Available".equalsIgnoreCase(str(row,2,fmt))
                        ? LecturerTimeType.NOT_AVAILABLE : LecturerTimeType.PRIORITY;
                LocalTime start = excel.toRoundedLocalTime(excel.numeric(row, 3));
                LocalTime end   = excel.toRoundedLocalTime(excel.numeric(row, 4));
                out.add(new TimeRow(nik, day, type, start, end));
            }
            return out;
        } catch (IOException e) { throw new ExcelReadException(e.getMessage()); }
    }
}
```

### 6.2 `lecturer/LecturerService.java` — add imports

> Inject `lecturerExcelService` and `jurusanRepository` (for `resolveHomeBase`), plus a `@Value` for the default. `importLecturers` takes the importing user's `faculty` (the controller pulls it from the JWT, same pattern as `CourseController.getCurrentUserFaculty()`).

```java
@Transactional
public ImportLog importLecturers(MultipartFile file, String faculty) {
    ImportLog log = new ImportLog("lecturer");
    for (LecturerRow row : lecturerExcelService.parseLecturers(file)) {
        String nik = row.nik() == null ? "" : row.nik().trim();
        String id = nik + "-" + row.name();
        if (nik.isBlank()) continue;
        if (lecturerRepository.existsByNik(nik)) {
            log.fail(id, "Ditemukan duplikat NIK pada database.");
            continue;
        }
        try {
            Lecturer l = new Lecturer();
            l.setNik(nik);
            l.setName(row.name());
            // home_base: legacy rule unknown (Laravel never set it — not fillable).
            // Routed through a single resolver so it's a one-line patch later. See resolveHomeBase().
            l.setHomeBase(resolveHomeBase(faculty));
            lecturerRepository.save(l);
            log.ok(id);
        } catch (Exception e) {
            log.fail(id, "Exception: " + e.getMessage());
        }
    }
    validateLockService.lock();
    return log;
}

// ---- home_base: the ONE place the guess lives (patch here when the rule is known) ----

@Value("${app.import.default-home-base:1}")
private Integer defaultHomeBase;

/**
 * Resolve a lecturer's home_base on import.
 * LEGACY UNKNOWN: Laravel never wrote this column (absent from $fillable and the sheet).
 * Current strategy: the importing user's faculty jurusan id, else the configured default
 * (which the seeder proves exists, so the FK to jurusans stays valid).
 * When the real rule surfaces, change ONLY this method (or flip app.import.default-home-base).
 */
private Integer resolveHomeBase(String faculty) {
    if (faculty != null && !faculty.isBlank()) {
        return jurusanRepository.findByFaculty(faculty).stream()
                .findFirst().map(Jurusan::getId).orElse(defaultHomeBase);
    }
    return defaultHomeBase;
}

@Transactional
public ImportLog importLecturerTimes(MultipartFile file) {
    ImportLog log = new ImportLog("lecturer-time");
    Map<String, Lecturer> byNik = lecturerRepository.findAll().stream()
            .collect(Collectors.toMap(Lecturer::getNik, l -> l, (a, b) -> a));
    for (TimeRow row : lecturerExcelService.parseTimes(file)) {
        Lecturer lecturer = byNik.get(row.nik());
        if (lecturer == null) { log.fail(row.nik(), "NIK tidak ditemukan."); continue; }
        // dedupe on (lecturer, day, type, start, end) — mirrors the Laravel existence check
        boolean exists = timeRepository.existsByLecturer_IdAndDayAndTypeAndStartTimeAndEndTime(
                lecturer.getId(), row.day(), row.type(), row.start(), row.end());
        if (exists) continue;
        try {
            LecturerTimeNA t = new LecturerTimeNA();
            t.setLecturer(lecturer);
            t.setDay(row.day());
            t.setType(row.type());
            t.setStartTime(row.start());
            t.setEndTime(row.end());
            timeRepository.save(t);
            log.ok(row.nik());
        } catch (Exception e) {
            log.fail(row.nik(), "Exception: " + e.getMessage());
        }
    }
    validateLockService.lock();
    return log;
}
```

> **New repository method** on `LecturerTimeNARepository`:
> ```java
> boolean existsByLecturer_IdAndDayAndTypeAndStartTimeAndEndTime(
>     Integer lecturerId, Integer day, LecturerTimeType type, LocalTime start, LocalTime end);
> ```

### 6.3 `lecturer/LecturerController.java`

```java
@GetMapping("/export")        public ResponseEntity<Resource> export()      { return lecturerExcelService.downloadTemplate(); }
@GetMapping("/export-time")   public ResponseEntity<Resource> exportTime()  { return lecturerExcelService.downloadTimeTemplate(); }

@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(service.importLecturers(file, getCurrentUserFaculty()));
}
@PostMapping(value = "/import-time", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ImportLog> importTime(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(service.importLecturerTimes(file));
}
```

---

## 7. Activity Excel

### 7.1 `activity/ActivityExcelService.java`

Mirrors `ActivitiesExcel`. Two exports (template + all-data) and one parse.

**Upload column map** (from `ActivitiesExcel::model`): `B(1)=course_code, D(3)=course_class, E(4)=course_session, F(5)=duration, G(6)=quota, H(7)=activity_type, I(8)=lecturers, J(9)=room_types, K(10)=room_specifics`. Multi-value cells (lecturers/room types/rooms) are split on `|`.

**All-data export** (`downloadAllAsExcel`) writes current-semester activities into `Empty Template.xlsx` with header `[Course Code, Course Name, Course Class, Course Session, Duration (jam), Quota, Course Type, Lecturer]`, one lecturer name per constraint.

```java
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

    public ResponseEntity<Resource> downloadTemplate() {
        Workbook wb = excel.openTemplate("template-activity.xlsx");
        // Build hidden "Data" sheet: A=course code, B=course name, C=activity types,
        // D=lecturer nik, E=lecturer name, F=room type, G=room code — plus dropdown/VLOOKUP
        // wiring exactly as ActivitiesExcel::setCourses()/setActivityType(). (See §7.4 note.)
        // ... (full template wiring detailed in code; omitted here for brevity)
        return excel.download(wb, "Activity Template.xlsx");
    }

    public ResponseEntity<Resource> downloadAll() {
        Integer sem = semesterRepository.findByCurrentTrue()
            .orElseThrow(() -> new BadRequestException("No current semester")).getId();
        List<Activity> acts = activityRepository.findBySemester_Id(sem);
        Map<String,String> nameByNik = lecturerRepository.findAll().stream()
            .collect(Collectors.toMap(Lecturer::getNik, Lecturer::getName, (a,b)->a));

        Workbook wb = excel.openTemplate("empty-template.xlsx");
        Sheet sheet = wb.getSheetAt(0);
        excel.writeHeader(sheet, "Course Code","Course Name","Course Class","Course Session",
                "Duration (jam)","Quota","Course Type","Lecturer");
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

    public List<ActivityRow> parse(MultipartFile file) {
        try (Workbook wb = excel.open(file)) {
            // NOTE: ActivitiesExcel::getFile stops when column B (course_code) is blank,
            // so read with keyColumn = 1.
            return excel.readRows(wb, 0, 1).stream().map(r -> new ActivityRow(
                at(r,1), at(r,3), parseInt(at(r,4)), parseInt(at(r,5)), parseInt(at(r,6)),
                at(r,7), split(at(r,8)), split(at(r,9)), split(at(r,10)))).toList();
        } catch (IOException e) { throw new ExcelReadException(e.getMessage()); }
    }

    private static List<String> split(String v) {
        if (v == null || v.isBlank()) return List.of();
        return Arrays.stream(v.split("\\|")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
}
```

### 7.2 `activity/ActivityService.java` — add `importActivities`

Mirrors `ActivityController@uploadExcel`: per row, validate (duplicate in current semester, every lecturer nik exists, course exists, every room code exists, every room-type name exists), then create the activity + its `ActivityConstraint` rows (Lecturer=nik, RoomType=**id**, Room=**id**) transactionally.

```java
@Transactional
public ImportLog importActivities(MultipartFile file) {
    ImportLog log = new ImportLog("activity");
    Semester sem = currentSemester();

    Map<String,Integer> roomTypeIdByName = roomTypeRepository.findAll().stream()
            .collect(Collectors.toMap(RoomType::getName, RoomType::getId, (a,b)->a));
    Map<String,Integer> roomIdByCode = roomRepository.findAll().stream()
            .collect(Collectors.toMap(Room::getRoomCode, Room::getId, (a,b)->a));

    for (ActivityExcelService.ActivityRow row : activityExcelService.parse(file)) {
        String id = row.courseCode() + "(" + row.courseClass() + ")-" + row.courseSession();
        try {
            if (activityRepository.existsBySemester_IdAndCourse_CodeAndCourseClassAndCourseSession(
                    sem.getId(), row.courseCode(), row.courseClass(), row.courseSession())) {
                log.fail(id, "Ditemukan duplikat pada database untuk semester saat ini."); continue;
            }
            if (row.lecturerNiks().stream().anyMatch(n -> !lecturerRepository.existsByNik(n))) {
                log.fail(id, "Salah satu NIK pengajar tidak ada pada database."); continue;
            }
            if (courseRepository.findByCode(row.courseCode()).isEmpty()) {
                log.fail(id, "Mata kuliah tidak ada pada database."); continue;
            }
            if (row.roomCodes().stream().anyMatch(c -> !roomIdByCode.containsKey(c))) {
                log.fail(id, "Salah satu Ruangan tidak ada pada database."); continue;
            }
            if (row.roomTypeNames().stream().anyMatch(t -> !roomTypeIdByName.containsKey(t))) {
                log.fail(id, "Salah satu Tipe Ruangan tidak ada pada database."); continue;
            }
            ActivityType type = activityTypeRepository.findByName(row.activityType())
                    .orElseGet(() -> activityTypeRepository.findById(1).orElseThrow());

            Activity a = new Activity();
            a.setSemester(sem);
            a.setCourse(courseRepository.findByCode(row.courseCode()).get());
            a.setCourseClass(row.courseClass());
            a.setCourseSession(row.courseSession());
            a.setDuration(row.duration());
            a.setQuota(row.quota());
            a.setActivityType(type);
            Activity saved = activityRepository.save(a);

            for (String nik : row.lecturerNiks())
                saveConstraint(saved, ConstraintType.LECTURER, nik);
            for (String rt : row.roomTypeNames())
                saveConstraint(saved, ConstraintType.ROOM_TYPE, String.valueOf(roomTypeIdByName.get(rt)));
            for (String rc : row.roomCodes())
                saveConstraint(saved, ConstraintType.ROOM, String.valueOf(roomIdByCode.get(rc)));

            log.ok(id);
        } catch (Exception e) {
            log.fail(id, "Exception: " + e.getMessage());
        }
    }
    validateLockService.lock();
    return log;
}

private void saveConstraint(Activity a, ConstraintType type, String value) {
    ActivityConstraint c = new ActivityConstraint();
    c.setActivity(a); c.setType(type); c.setValue(value.trim());
    constraintRepository.save(c);
}
```

> Needs `activityTypeRepository.findByName(String)` (add if missing). `roomTypeRepository`/`roomRepository` must be injected into `ActivityService` (currently not present — add them).

### 7.3 `activity/ActivityController.java`

```java
@GetMapping("/export")      public ResponseEntity<Resource> export()    { return activityExcelService.downloadTemplate(); }
@GetMapping("/export-all")  public ResponseEntity<Resource> exportAll() { return activityExcelService.downloadAll(); }

@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(activityService.importActivities(file));
}
```

### 7.4 Activity template complexity note

`ActivitiesExcel::generateTemplate` uses a `VLOOKUP` formula to auto-fill the course name from the picked code, plus two dropdowns (course code, activity type). POI supports both `sheet.getRow(i).createCell(j).setCellFormula("VLOOKUP(...)")` and `createFormulaListConstraint`. Because this is the most intricate template, budget extra time here and verify the generated file opens in Excel/LibreOffice without repair prompts. If VLOOKUP fidelity proves fragile, an acceptable fallback is to drop the auto-name column and keep only the dropdowns (course name is re-derived server-side on import anyway).

---

## 8. Result Excel

`ResultsExcel` has **no template**; it produces two exports (SIAKAD, printable) and one import. Faculty scoping in Laravel uses `Jurusan::jurusanIds()` — the Spring equivalent is `jurusanService.getJurusanIds(faculty)`.

### 8.1 `result/ResultExcelService.java`

**SIAKAD export** (`download`): two sheets.
- Sheet "SIAKAD": valid results (`valid=1`) with columns `[No, Kode Matkul, Nama Kelas, Kelas, Sesi, Hari, Waktu Mulai, Waktu Berakhir, Ruang, Jumlah, Jenis Temu, NIK, Pengajar]`. `Hari` = `['Minggu','Senin',…,'Sabtu'][day]`. Times formatted `HH:mm`. `NIK` = pipe/`|`-joined lecturer NIKs; `Pengajar` = joined names.
- Sheet "Not Inserted": invalid results (`valid=0`) without day/time/room columns.

**Printable export** (`downloadPrint`): one sheet per day; rows = rooms, columns = hour blocks `7-8 … 23-24`; each scheduled activity written into its start-hour cell with merged cells across its duration and a background fill from `course.colorHex`. This is the most involved writer — see the mapping in `ResultsExcel::downloadPrint`. **Recommendation:** implement SIAKAD first (verification-critical), then the print grid; the print grid can ship as a follow-up if time-boxed.

```java
@Component
@RequiredArgsConstructor
public class ResultExcelService {
    private final ExcelService excel;
    private final ResultRepository resultRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final JurusanService jurusanService;
    private final ActivityConstraintRepository constraintRepository;

    private static final String[] DAY = {"Minggu","Senin","Selasa","Rabu","Kamis","Jumat","Sabtu"};

    public ResponseEntity<Resource> exportSiakad(Integer semesterId, String faculty) {
        List<Integer> jurusanIds = jurusanService.getJurusanIds(faculty);
        List<Result> valid   = resultRepository.findForExport(semesterId, true,  jurusanIds);
        List<Result> invalid = resultRepository.findForExport(semesterId, false, jurusanIds);
        Map<String,String> nameByNik = lecturerRepository.findAll().stream()
                .collect(Collectors.toMap(Lecturer::getNik, Lecturer::getName, (a,b)->a));

        Workbook wb = excel.openTemplate("empty-template.xlsx");
        Sheet siakad = wb.getSheetAt(0); siakad.setSheetName(0, "SIAKAD"); // rename existing sheet
        excel.writeHeader(siakad, "No","Kode Matkul","Nama Kelas","Kelas","Sesi","Hari",
                "Waktu Mulai","Waktu Berakhir","Ruang","Jumlah","Jenis Temu","NIK","Pengajar");
        int r = 1;
        for (Result res : valid) {
            Activity a = res.getActivity();
            List<String> niks = constraintRepository.findByActivity_IdAndType(a.getId(), ConstraintType.LECTURER)
                    .stream().map(ActivityConstraint::getValue).toList();
            excel.setCell(siakad, r, 0, String.valueOf(r));
            excel.setCell(siakad, r, 1, a.getCourse().getCode());
            excel.setCell(siakad, r, 2, a.getCourse().getName());
            excel.setCell(siakad, r, 3, a.getCourseClass());
            excel.setCell(siakad, r, 4, String.valueOf(a.getCourseSession()));
            excel.setCell(siakad, r, 5, DAY[Integer.parseInt(res.getDay())]);
            excel.setCell(siakad, r, 6, res.getStartTime() == null ? "" : res.getStartTime().toString());
            excel.setCell(siakad, r, 7, res.getEndTime()   == null ? "" : res.getEndTime().toString());
            excel.setCell(siakad, r, 8, res.getRoom() != null ? res.getRoom().getRoomCode() : "");
            excel.setCell(siakad, r, 9, String.valueOf(a.getQuota()));
            excel.setCell(siakad, r,10, a.getActivityType().getName());
            excel.setCell(siakad, r,11, String.join("|", niks));
            excel.setCell(siakad, r,12, niks.stream().map(n -> nameByNik.getOrDefault(n,n)).collect(Collectors.joining("|")));
            r++;
        }
        // + "Not Inserted" sheet for `invalid` (9 columns, no day/time/room)
        return excel.download(wb, "Siakad Export.xlsx");
    }
    // exportPrint(...) — see §8.1
}
```

> **New repository query** on `ResultRepository` (faculty-scoped, valid flag):
> ```java
> @Query("""
>     select r from Result r
>       join r.activity a join a.course c
>      where r.semester.id = :sem and r.valid = :valid and c.jurusan.id in :jurusanIds
>     """)
> List<Result> findForExport(Integer sem, boolean valid, List<Integer> jurusanIds);
> ```
> `Room.roomCode` is used above — confirm the getter name (`getRoomCode()`). For the print grid's cell fill, the entity needs a `colorHex`/color accessor on `Course`; the current `Course.getColor()` returns an HSL string. **Add** a `getColorHex()` (ARGB hex) helper or convert HSL→ARGB in `ResultExcelService` — flag this for the print export.

### 8.2 `result/ResultService.java` — add `importResults`

Mirrors `ResultController@uploadExcelResult`. Result columns: `A(0)=no, B(1)=code, D(3)=class, E(4)=session, F(5)=day(name), G(6)=start, H(7)=end, I(8)=room_code, J(9)=quota, K(10)=act_type, L(11)=nik`. Day map here is `Senin=1…Jumat=5`. NIKs split on `,` (comma, not pipe).

Logic per row:
1. Find existing `Result` whose activity matches `(code,class,session)` → if found and room resolves, update its `room/day/times`.
2. Else find/create the `Activity` (duration = |end−start| in hours, activity type by name defaulting to id 1), create its Lecturer + RoomType constraints, then create a valid `Result`.

```java
@Transactional
public ImportLog importResults(MultipartFile file) {
    ImportLog log = new ImportLog("result");
    Semester sem = currentSemester();
    for (ResultExcelService.ResultRow h : resultExcelService.parse(file)) {
        String id = h.courseCode()+"("+h.courseClass()+")-"+h.courseSession();
        try {
            Optional<Result> existing = resultRepository.findFirstByActivityKey(
                    h.courseCode(), h.courseClass(), h.courseSession());
            Room room = roomRepository.findByRoomCode(h.roomCode()).orElse(null);
            if (existing.isPresent()) {
                if (room != null) {
                    Result res = existing.get();
                    res.setRoom(room); res.setDay(String.valueOf(h.day()));
                    res.setStartTime(h.start()); res.setEndTime(h.end());
                    resultRepository.save(res);
                    log.ok(id);
                }
            } else {
                Activity activity = activityRepository
                        .findFirstByCourse_CodeAndCourseClassAndCourseSession(
                                h.courseCode(), h.courseClass(), h.courseSession())
                        .orElseGet(() -> createActivityFromResult(sem, h));
                if (room != null) {
                    // lecturer constraints (comma-separated) + room-type constraint
                    for (String nik : h.niks()) saveActivityConstraint(activity, ConstraintType.LECTURER, nik);
                    saveActivityConstraint(activity, ConstraintType.ROOM_TYPE,
                            String.valueOf(room.getRoomType().getId()));
                    Result res = new Result();
                    res.setSemester(sem); res.setActivity(activity); res.setRoom(room);
                    res.setDay(String.valueOf(h.day()));
                    res.setStartTime(h.start()); res.setEndTime(h.end());
                    res.setValid(true);
                    resultRepository.save(res);
                    log.ok(id);
                }
            }
        } catch (Exception e) { log.fail(id, "Exception: " + e.getMessage()); }
    }
    validateLockService.lock();
    return log;
}
```

> **New repository methods:**
> `ResultRepository.findFirstByActivityKey(code,class,session)` (JPQL joining activity),
> `ActivityRepository.findFirstByCourse_CodeAndCourseClassAndCourseSession(...)`.
> `ResultService` must inject `roomRepository`, `activityRepository`, `constraintRepository`, `activityTypeRepository`, `resultExcelService`.

### 8.3 `result/ResultController.java` — replace `exportStub`

```java
// remove the exportStub method + its imports (ResponseStatusException)

private final ResultExcelService resultExcelService;   // add to constructor

@GetMapping("/export-siakad/{semesterId}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Resource> exportSiakad(@PathVariable Integer semesterId) {
    return resultExcelService.exportSiakad(semesterId, currentFaculty());
}

@GetMapping("/export-print/{semesterId}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Resource> exportPrint(@PathVariable Integer semesterId) {
    return resultExcelService.exportPrint(semesterId, currentFaculty());
}

@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ImportLog> importExcel(@RequestParam("file") MultipartFile file) {
    return ResponseEntity.ok(service.importResults(file));
}

private String currentFaculty() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.getDetails() instanceof String f) ? f : null;
}
```

---

## 9. Endpoint Summary

| Resource | Method | Path | Auth | Laravel origin |
|----------|--------|------|------|----------------|
| Course   | GET  | `/api/courses/export`               | Auth  | `excel-course` |
| Course   | POST | `/api/courses/import`               | Auth  | `uploads-course` |
| Room     | GET  | `/api/rooms/export`                 | Auth  | `excel-room` |
| Room     | POST | `/api/rooms/import`                 | Auth  | `uploads-room` |
| Lecturer | GET  | `/api/lecturers/export`             | Auth  | `excel-lecturer` |
| Lecturer | GET  | `/api/lecturers/export-time`        | Auth  | `excel-lecturer-time` |
| Lecturer | POST | `/api/lecturers/import`             | Auth  | `uploads-lecturer` |
| Lecturer | POST | `/api/lecturers/import-time`        | Auth  | `uploads-lecturer-time` |
| Activity | GET  | `/api/activities/export`            | Auth  | `excel-activities` |
| Activity | GET  | `/api/activities/export-all`        | Auth  | `AllActivityExcel` |
| Activity | POST | `/api/activities/import`            | Auth  | `uploads-activities` |
| Result   | GET  | `/api/results/export-siakad/{id}`   | ADMIN | `export-siakad/{id}` |
| Result   | GET  | `/api/results/export-print/{id}`    | ADMIN | `download-print/{id}` |
| Result   | POST | `/api/results/import`               | ADMIN | `uploads-excel-result-update` |

All export responses: `200`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment; filename="…"`.
All import responses: `200` with `ImportLog` JSON, or `400` `ErrorResponse` on unreadable file.

---

## 10. Cross-Cutting Concerns

1. **Multipart config.** Spring Boot enables multipart by default (`spring.servlet.multipart.enabled=true`). Add generous limits to `application.properties` because these files can be large:
   ```properties
   spring.servlet.multipart.max-file-size=25MB
   spring.servlet.multipart.max-request-size=25MB
   ```
   (Laravel effectively removed limits via `ini_set('memory_limit', -1)`.)

   Also add the single knob for the `home_base` guess (see D2):
   ```properties
   # Fallback jurusan id for imported lecturers when the importer's faculty can't be resolved.
   # Legacy rule for lecturers.home_base is undocumented — change this (or LecturerService.resolveHomeBase) when known.
   app.import.default-home-base=1
   ```

2. **Security & browser downloads.** Every endpoint is already `authenticated()` in `SecurityConfig`. GET download links opened directly by the browser won't carry the `Authorization` header — the frontend must fetch with the bearer token and save the blob (as the roadmap notes the frontend is out of scope for now, no backend change is needed).

3. **Faculty scoping.** Result exports and (in Laravel) course/activity listings scope by `Jurusan::jurusanIds()`. Reuse `jurusanService.getJurusanIds(faculty)`. Course/room/lecturer imports are **not** faculty-scoped in Laravel — keep parity.

4. **`validateLockService.lock()`** is called after every successful import batch, matching the Laravel `$this->vlock->lock()` calls, so the slot-validation engine (Phase 7) knows data changed.

5. **Transactions.** Each `importXxx` is `@Transactional` at the batch level in Laravel? No — Laravel commits per row implicitly. To preserve "partial success with a log", **do not** wrap the whole batch so one bad row rolls back everything. Either (a) drop `@Transactional` on the batch method and rely on per-`save` autocommit, or (b) keep per-row work in a `REQUIRES_NEW` inner method. **Recommendation:** extract the per-row persistence into a helper annotated `@Transactional(propagation = REQUIRES_NEW)` on a separate bean so a failed row logs and continues without poisoning the others.

6. **Number formatting.** `DataFormatter` returns `"120"` for an integer-typed cell but could return `"120.5"` or locale-grouped text for oddly formatted cells; `parseInt` trims and null-guards. Session/duration/quota are parsed defensively.

---

## 11. Verification Criteria

- [ ] `./gradlew build` compiles with the new files.
- [ ] `GET /api/courses/export` downloads a valid `.xlsx` that opens without a repair prompt and shows the jurusan/type/tingkat/konsentrasi dropdowns.
- [ ] `POST /api/courses/import` with a filled template creates courses; a duplicate `code` and a bad konsentrasi both land in `log.failed` with the Indonesian messages; good rows land in `log.succeeded`.
- [ ] Room import creates parents before children; a child's `parent_room_id` resolves correctly.
- [ ] Lecturer import skips existing NIKs; lecturer-time import rounds `07:15→07:30`, `07:45→08:00`, and dedupes identical rows.
- [ ] Activity import rejects rows with unknown course/lecturer/room/room-type and creates constraints (Lecturer=nik, Room=id, RoomType=id) for good rows.
- [ ] `GET /api/activities/export-all` returns current-semester activities with joined lecturer names.
- [ ] `GET /api/results/export-siakad/{id}` produces "SIAKAD" + "Not Inserted" sheets scoped to the caller's faculty; times render `HH:mm`; day names correct.
- [ ] `GET /api/results/export-print/{id}` produces one sheet per day with room rows and merged/colored activity blocks (or is explicitly deferred).
- [ ] `POST /api/results/import` updates existing results and creates missing activity+constraints+result.
- [ ] Uploading a non-`.xlsx` / corrupt file returns `400` with the standard `ErrorResponse`.

---

## 12. Open Decisions (flag for review)

| # | Decision | Default taken |
|---|----------|---------------|
| D1 | Return `ImportLog` (rich) vs `ImportResultResponse` (flat) from imports | `ImportLog` (parity with Laravel log) |
| D2 | `Lecturer.home_base` legacy rule unknown (Laravel never wrote it; absent from the sheet; the 2021 `timetab.sql` dump lacks the column entirely) | **Provisional, patchable:** write via `LecturerService.resolveHomeBase(faculty)` → importer's faculty jurusan id, else `app.import.default-home-base` (=1, FK-safe). One-line patch when the rule is known. **Precondition:** if `ddl-auto=validate` ever fails on `home_base`, the live column doesn't exist → drop the field from `Lecturer`/`LecturerRequest`/`LecturerResponse` instead. |
| D3 | Room import duplicate-`room_code` guard (Laravel has none) | keep Laravel behavior (no guard) |
| D4 | Activity template VLOOKUP fidelity | implement; fall back to dropdown-only if fragile |
| D5 | Print (`export-print`) grid coloring needs `Course` ARGB hex | add `Course.getColorHex()` / HSL→ARGB, or defer print export |
| D6 | Batch transaction strategy | per-row `REQUIRES_NEW` so one bad row doesn't roll back the batch |

---

## 13. File Count

| Category | Count |
|----------|-------|
| New Java files | 9 (`ExcelService`, `ExcelReadException`, `ImportLog`, 5 domain `*ExcelService`, +`ImportResponse` record if extracted) |
| New resource files | 6 (`.xlsx` templates copied from `_project_data/`) |
| Edited Java files | 12 (5 controllers, 5 services, 2–3 repositories, `application.properties`) |
| Deleted files | 0 |

*End of Phase 9 plan.*
