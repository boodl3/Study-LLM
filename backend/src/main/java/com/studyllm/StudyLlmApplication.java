package com.studyllm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StudyLlmApplication {

  public static void main(String[] args) {
    SpringApplication.run(StudyLlmApplication.class, args);
  }
}
