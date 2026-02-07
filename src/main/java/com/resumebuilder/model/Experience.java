package com.resumebuilder.model;

import lombok.Data;

import java.util.List;

@Data
public class Experience {

    private String company;
    private String role;
    private String location;
    private String startDate;
    private String endDate;
    private List<String> responsibilities;
}
