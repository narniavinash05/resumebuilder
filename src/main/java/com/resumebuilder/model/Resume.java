package com.resumebuilder.model;

import lombok.Data;

import java.util.List;

@Data
public class Resume {

    private String fullName;
    private ContactInfo contactInfo;
    private String professionalSummary;

    private List<Experience> experiences;
    private List<Education> education;
    private List<String> skills;
    private List<Certification> certifications;
}
