package com.openmanus.demo.studyplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.openmanus.saa", "com.openmanus.demo.studyplan"})
public class StudyPlanDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyPlanDemoApplication.class, args);
    }
}
