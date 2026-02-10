package com.resumebuilder.model;

import lombok.Data;

import java.util.List;

@Data
public class Resume {

    private String fullName;
    private String professionalSummary;
    private ContactInfo contactInfo;
    private List<Experience> experiences;
    private List<Education> education;
    private List<SkillCategory> skillCategories;
}
