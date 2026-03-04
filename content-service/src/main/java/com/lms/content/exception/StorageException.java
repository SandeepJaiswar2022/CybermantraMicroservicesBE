package com.lms.content.exception;

/** Thrown when an upload or delete operation on the storage backend fails. */
public class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
