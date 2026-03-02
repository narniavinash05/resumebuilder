package com.resumebuilder.model;

import lombok.Data;
import java.util.List;

@Data
public class SkillCategory {
    private String categoryName;
    private List<String> skills;
}
