package com.person.repit.interview.repository;

import com.person.repit.interview.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {
}
