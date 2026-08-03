package com.studyllm.common;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
