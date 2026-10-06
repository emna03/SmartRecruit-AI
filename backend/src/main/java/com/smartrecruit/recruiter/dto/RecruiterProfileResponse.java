package com.smartrecruit.recruiter.dto;

import com.smartrecruit.company.dto.CompanySummary;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.user.entity.User;

public record RecruiterProfileResponse(
        Long id,
        Long userId,
        String email,
        String firstName,
        String lastName,
        String jobTitle,
        String phone,
        CompanySummary company) {

    public static RecruiterProfileResponse from(RecruiterProfile p) {
        User u = p.getUser();
        return new RecruiterProfileResponse(p.getId(), u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                p.getJobTitle(), p.getPhone(), CompanySummary.from(p.getCompany()));
    }
}
