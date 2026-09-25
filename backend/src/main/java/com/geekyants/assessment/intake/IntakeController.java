package com.geekyants.assessment.intake;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.config.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Entry point for the existing recruitment software. */
@RestController
@RequestMapping("/api/v1/intake")
class IntakeController {

    private final IntakeService intake;
    private final CurrentUser currentUser;

    IntakeController(IntakeService intake, CurrentUser currentUser) {
        this.intake = intake;
        this.currentUser = currentUser;
    }

    record IntakeResponse(String assignmentId, String status) {
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.ACCEPTED)
    IntakeResponse create(@Valid @RequestBody IntakeRequest request) {
        Assignment assignment = intake.receive(request, currentUser.subject());
        return new IntakeResponse(assignment.getPublicId(), assignment.getStatus().name());
    }
}
