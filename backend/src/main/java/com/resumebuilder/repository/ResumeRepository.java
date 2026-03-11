package com.resumebuilder.repository;

import com.resumebuilder.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    List<ResumeEntity> findByUserEmail(String email);
}
