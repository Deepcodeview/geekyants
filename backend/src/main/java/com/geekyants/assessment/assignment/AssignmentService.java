package com.geekyants.assessment.assignment;

import com.geekyants.assessment.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {

    private final AssignmentRepository assignments;

    public AssignmentService(AssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public Assignment get(String publicId) {
        return assignments.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Assignment", publicId));
    }
}
