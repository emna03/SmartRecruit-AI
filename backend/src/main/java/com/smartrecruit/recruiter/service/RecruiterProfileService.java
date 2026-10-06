package com.smartrecruit.recruiter.service;

import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.recruiter.dto.RecruiterProfileResponse;
import com.smartrecruit.recruiter.dto.UpdateRecruiterProfileRequest;
import com.smartrecruit.recruiter.entity.RecruiterProfile;
import com.smartrecruit.recruiter.repository.RecruiterProfileRepository;
import com.smartrecruit.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.smartrecruit.common.util.Strings.blankToNull;

@Service
@RequiredArgsConstructor
public class RecruiterProfileService {

    private final RecruiterProfileRepository recruiterProfileRepository;

    @Transactional(readOnly = true)
    public RecruiterProfile getByUserId(Long userId) {
        return recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil recruteur introuvable"));
    }

    /** Entreprise du recruteur connecté ; erreur explicite s'il ne l'a pas encore créée. */
    @Transactional(readOnly = true)
    public Company requireCompany(Long userId) {
        Company company = getByUserId(userId).getCompany();
        if (company == null) {
            throw new BadRequestException("Vous devez d'abord créer votre entreprise");
        }
        return company;
    }

    @Transactional(readOnly = true)
    public RecruiterProfileResponse getProfile(Long userId) {
        return RecruiterProfileResponse.from(getByUserId(userId));
    }

    @Transactional
    public RecruiterProfileResponse updateProfile(Long userId, UpdateRecruiterProfileRequest request) {
        RecruiterProfile profile = getByUserId(userId);
        User user = profile.getUser();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        profile.setJobTitle(blankToNull(request.jobTitle()));
        profile.setPhone(blankToNull(request.phone()));
        return RecruiterProfileResponse.from(profile);
    }
}
