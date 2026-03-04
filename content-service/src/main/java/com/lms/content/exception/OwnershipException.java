package com.lms.content.exception;

/** Thrown when a user attempts to access or modify content they do not own. */
public class OwnershipException extends RuntimeException {
    public OwnershipException(String message) { super(message); }
}
