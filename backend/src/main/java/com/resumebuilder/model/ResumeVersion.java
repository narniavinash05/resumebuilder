package com.resumebuilder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "resume_versions")
@Getter
@Setter
public class ResumeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "resume_id")
    private ResumeEntity resume;

    // Store structured resume JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> resumeJson;

    private Integer atsScore;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
