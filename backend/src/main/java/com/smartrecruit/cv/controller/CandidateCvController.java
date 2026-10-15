package com.smartrecruit.cv.controller;

import com.smartrecruit.cv.dto.CvImportRequest;
import com.smartrecruit.cv.dto.CvImportResponse;
import com.smartrecruit.cv.dto.CvResponse;
import com.smartrecruit.cv.dto.ProfessionalSummaryResponse;
import com.smartrecruit.cv.service.CvService;
import com.smartrecruit.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * CV du candidat connecté (rôle CANDIDAT, voir SecurityConfig). Aucun identifiant dans l'URL :
 * un candidat ne peut agir que sur son propre CV.
 */
@RestController
@RequestMapping("/api/candidate/cv")
@RequiredArgsConstructor
public class CandidateCvController {

    private final CvService cvService;

    @GetMapping
    public CvResponse get(@AuthenticationPrincipal AppUserDetails user) {
        return cvService.get(user.getId());
    }

    /** Importe ou remplace le CV (champ multipart « file ») puis l'analyse. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CvResponse upload(@AuthenticationPrincipal AppUserDetails user, @RequestPart("file") MultipartFile file) {
        return cvService.upload(user.getId(), file);
    }

    @PostMapping("/analyze")
    public CvResponse analyze(@AuthenticationPrincipal AppUserDetails user) {
        return cvService.reanalyze(user.getId());
    }

    @PostMapping("/import")
    public CvImportResponse importIntoProfile(@AuthenticationPrincipal AppUserDetails user,
                                              @RequestBody CvImportRequest request) {
        return cvService.importIntoProfile(user.getId(), request);
    }

    /**
     * Génère un résumé professionnel via le LLM (Gemini), à partir des données déjà extraites du CV.
     * Aucune identité ni PDF n'est transmis au fournisseur.
     */
    @PostMapping("/professional-summary")
    public ProfessionalSummaryResponse professionalSummary(@AuthenticationPrincipal AppUserDetails user) {
        return cvService.generateProfessionalSummary(user.getId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails user) {
        cvService.delete(user.getId());
    }
}
