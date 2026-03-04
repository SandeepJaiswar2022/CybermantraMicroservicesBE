package com.lms.content.exception;

/** Thrown when a content item is not found in the database. */
public class ContentNotFoundException extends RuntimeException {
    public ContentNotFoundException(String message) { super(message); }
}
