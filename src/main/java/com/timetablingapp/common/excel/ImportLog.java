package com.timetablingapp.common.excel;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured import result, mirroring Laravel's generateLog($failed, $succeed, $desc) payload
 * ({ filename, failed:[{id,message}], succeed:[{id,message}] }).
 */
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

    public void ok(String id) {
        succeeded.add(new Entry(id, ""));
    }

    public void fail(String id, String message) {
        failed.add(new Entry(id, message));
    }

    /** Legacy `msg` flag: "1" when everything succeeded, "0" when anything failed. */
    public String getMsg() {
        return failed.isEmpty() ? "1" : "0";
    }

    public int getImportedCount() {
        return succeeded.size();
    }
}
