package com.openmanus.saa.service.agent;

public interface SpecialistAgent {

    String name();

    String description();

    String execute(String objective, String currentPlan, String step);
}
