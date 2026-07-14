package com.timetablingapp.common.excel;

import com.timetablingapp.common.exception.BadRequestException;

/**
 * Thrown when an uploaded spreadsheet cannot be read or is malformed.
 * Extends {@link BadRequestException} so the GlobalExceptionHandler maps it to HTTP 400.
 */
public class ExcelReadException extends BadRequestException {
    public ExcelReadException(String message) {
        super(message);
    }
}
