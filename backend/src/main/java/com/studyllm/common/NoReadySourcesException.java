package com.studyllm.common;

/** Thrown when a chat question is asked in a notebook with zero READY sources (spec FR-018). */
public class NoReadySourcesException extends RuntimeException {
  public NoReadySourcesException(String message) {
    super(message);
  }
}
