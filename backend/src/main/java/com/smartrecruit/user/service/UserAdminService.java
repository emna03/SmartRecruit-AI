package com.smartrecruit.user.service;

import com.smartrecruit.candidate.repository.CandidateProfileRepository;
import com.smartrecruit.common.exception.BadRequestException;
import com.smartrecruit.recruiter.repository.RecruiterProfileRepository;
import com.smartrecruit.common.exception.ResourceNotFoundException;
import com.smartrecruit.cv.service.CvStorage;
import com.smartrecruit.user.dto.UserResponse;
import com.smartrecruit.user.entity.RoleName;
import com.smartrecruit.user.entity.User;
import com.smartrecruit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CvStorage cvStorage;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll(RoleName role) {
        List<User> users = role == null
                ? userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                : userRepository.findByRoleNameOrderByCreatedAtDesc(role);
        return users.stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return UserResponse.from(getUser(id));
    }

    @Transactional
    public UserResponse updateStatus(Long id, boolean enabled, Long currentAdminId) {
        if (id.equals(currentAdminId) && !enabled) {
            throw new BadRequestException("Un administrateur ne peut pas désactiver son propre compte");
        }
        User user = getUser(id);
        user.setEnabled(enabled);
        return UserResponse.from(user);
    }

    /**
     * Supprime le compte et son profil. Le profil candidat entraîne avec lui CV, formations,
     * expériences et candidatures (cascade JPA) ; les scores sont supprimés par la base (ON DELETE CASCADE).
     * Le fichier du CV (donnée personnelle) est effacé du disque une fois la suppression validée.
     */
    @Transactional
    public void delete(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BadRequestException("Un administrateur ne peut pas supprimer son propre compte");
        }
        User user = getUser(id);
        candidateProfileRepository.findByUserId(id).ifPresent(profile -> {
            if (profile.getCv() != null) {
                String storedFileName = profile.getCv().getStoredFileName();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cvStorage.delete(storedFileName);
                    }
                });
            }
            candidateProfileRepository.delete(profile);
        });
        recruiterProfileRepository.findByUserId(id).ifPresent(recruiterProfileRepository::delete);
        userRepository.delete(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
