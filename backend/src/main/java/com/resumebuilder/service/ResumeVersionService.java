package com.resumebuilder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.model.Resume;
import com.resumebuilder.model.ResumeVersion;
import com.resumebuilder.model.User;
import com.resumebuilder.repository.ResumeVersionRepository;
import com.resumebuilder.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ResumeVersionService {

    private final ResumeVersionRepository repo;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ResumeVersionService(ResumeVersionRepository repo,
                                UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public void saveResumeVersion(String email,
                                  String jobDescription,
                                  Resume resume) {

        try {

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ResumeVersion version = new ResumeVersion();

            // Convert Resume object → Map for jsonb storage
            Map<String, Object> resumeMap =
                    objectMapper.convertValue(resume, Map.class);

            version.setResumeJson(resumeMap);
            version.setJobDescription(jobDescription);
            version.setAtsScore(resume.getAtsScore());

            repo.save(version);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save resume version", e);
        }
    }
}
