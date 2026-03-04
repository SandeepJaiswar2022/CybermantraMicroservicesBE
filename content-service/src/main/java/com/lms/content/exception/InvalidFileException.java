package com.lms.content.exception;

/**
 * Thrown when an uploaded file fails validation:
 * wrong MIME type, exceeds size limit, corrupt content, etc.
 */
public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) { super(message); }
}
