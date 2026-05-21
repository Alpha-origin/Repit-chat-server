package com.person.repit.interview.repository;

import com.person.repit.interview.dto.response.InterviewResponse;
import com.person.repit.interview.entity.Interview;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserId(Long userId);
}
