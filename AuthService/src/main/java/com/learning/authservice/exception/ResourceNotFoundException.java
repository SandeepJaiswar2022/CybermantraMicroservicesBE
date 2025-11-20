package com.learning.authservice.exception;

public class ResourceNotFoundException extends RuntimeException{
	public ResourceNotFoundException(String message)
	{
		super(message);
	}

}
