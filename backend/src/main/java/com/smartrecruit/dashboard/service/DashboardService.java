package com.smartrecruit.dashboard.service;

import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.candidate.service.CandidateProfileService;
import com.smartrecruit.company.entity.Company;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.dashboard.dto.AdminDashboardResponse;
import com.smartrecruit.dashboard.dto.AdminDashboardResponse.MonthlyActivity;
import com.smartrecruit.dashboard.dto.AdminDashboardResponse.SkillCount;
import com.smartrecruit.dashboard.dto.CandidateDashboardResponse;
import com.smartrecruit.dashboard.dto.CandidateDashboardResponse.CvState;
import com.smartrecruit.dashboard.dto.CandidateDashboardResponse.CvSummary;
import com.smartrecruit.dashboard.dto.CandidateDashboardResponse.TopMatch;
import com.smartrecruit.dashboard.dto.MonthlyCount;
import com.smartrecruit.dashboard.dto.RecruiterDashboardResponse;
import com.smartrecruit.dashboard.dto.RecruiterDashboardResponse.OfferStats;
import com.smartrecruit.dashboard.dto.RecruiterDashboardResponse.TopCandidate;
import com.smartrecruit.dashboard.repository.DashboardQueryRepository;
import com.smartrecruit.dashboard.repository.DashboardQueryRepository.CvRow;
import com.smartrecruit.dashboard.repository.DashboardQueryRepository.ScoreStats;
import com.smartrecruit.dashboard.repository.EnumCounts;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.matching.service.MatchingResultMapper;
import com.smartrecruit.recruiter.service.RecruiterProfileService;
import com.smartrecruit.user.entity.RoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tableaux de bord : chaque statistique est calculée dans MySQL à la demande (rien n'est mis en cache ni stocké).
 * Le périmètre vient toujours de l'utilisateur connecté : son profil candidat ou l'entreprise du recruteur.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    static final int TOP_MATCHES = 5;
    static final int TOP_SKILLS = 8;
    static final int MONTHS = 6;

    private final DashboardQueryRepository queries;
    private final CandidateProfileService candidateProfileService;
    private final RecruiterProfileService recruiterProfileService;

    @Transactional(readOnly = true)
    public CandidateDashboardResponse candidate(Long userId) {
        Long candidateId = candidateProfileService.getByUserId(userId).getId();
        Map<ApplicationStatus, Long> byStatus = queries.applicationsByStatusOfCandidate(candidateId);
        Optional<CvRow> cv = queries.cvOf(candidateId);
        CvParsingStatus cvStatus = cv.map(CvRow::status).orElse(null);
        LocalDateTime cvParsedAt = cv.map(CvRow::parsedAt).orElse(null);
        List<TopMatch> topMatches = queries.topVisibleMatches(candidateId, TOP_MATCHES).stream()
                .map(m -> new TopMatch(m.jobOfferId(), m.title(), m.companyName(), m.offerStatus(),
                        m.score().doubleValue(), m.computedAt(),
                        MatchingResultMapper.isStale(m.computedAt(), cvStatus, cvParsedAt), m.applied()))
                .toList();
        return new CandidateDashboardResponse(EnumCounts.total(byStatus), byStatus,
                queries.countOffers(JobOfferStatus.PUBLIEE), queries.countPublishedOffersNotAppliedBy(candidateId),
                cv.map(DashboardService::cvSummary).orElse(new CvSummary(CvState.ABSENT, null, null, null, 0)),
                queries.countVisibleMatches(candidateId), topMatches);
    }

    @Transactional(readOnly = true)
    public RecruiterDashboardResponse recruiter(Long userId) {
        Company company = recruiterProfileService.getByUserId(userId).getCompany();
        if (company == null) {
            return RecruiterDashboardResponse.withoutCompany();
        }
        Long companyId = company.getId();
        Map<Long, Map<ApplicationStatus, Long>> applicationsByOffer = new HashMap<>();
        Map<ApplicationStatus, Long> applicationsByStatus = EnumCounts.zeros(ApplicationStatus.class);
        for (Object[] row : queries.applicationsByOfferAndStatus(companyId)) {
            ApplicationStatus status = (ApplicationStatus) row[1];
            long count = ((Number) row[2]).longValue();
            applicationsByOffer.computeIfAbsent((Long) row[0], id -> EnumCounts.zeros(ApplicationStatus.class))
                    .put(status, count);
            applicationsByStatus.merge(status, count, Long::sum);
        }
        Map<Long, ScoreStats> scores = queries.scoresByOffer(companyId);
        Map<JobOfferStatus, Long> offersByStatus = EnumCounts.zeros(JobOfferStatus.class);
        List<OfferStats> offers = queries.offersOfCompany(companyId).stream().map(offer -> {
            offersByStatus.merge(offer.status(), 1L, Long::sum);
            Map<ApplicationStatus, Long> counts = applicationsByOffer.getOrDefault(offer.id(),
                    EnumCounts.zeros(ApplicationStatus.class));
            ScoreStats score = scores.getOrDefault(offer.id(), new ScoreStats(0, null, null));
            return new OfferStats(offer.id(), offer.title(), offer.status(), offer.createdAt(),
                    EnumCounts.total(counts), counts, score.count(), round(score.average()), number(score.best()));
        }).toList();
        ScoreStats overall = queries.scoresOfCompany(companyId);
        List<TopCandidate> topCandidates = queries.topCandidates(companyId, TOP_MATCHES).stream()
                .map(c -> new TopCandidate(c.applicationId(), c.firstName() + " " + c.lastName(), c.jobOfferId(),
                        c.jobTitle(), c.score().doubleValue(), c.status(),
                        MatchingResultMapper.isStale(c.computedAt(), c.cvStatus(), c.cvParsedAt())))
                .toList();
        return new RecruiterDashboardResponse(true, company.getName(), offers.size(), offersByStatus,
                EnumCounts.total(applicationsByStatus), applicationsByStatus,
                monthly(queries.applicationsByMonthOfCompany(companyId, firstDayOfPeriod())),
                overall.count(), round(overall.average()), offers, topCandidates);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse admin() {
        Map<RoleName, Long> usersByRole = queries.usersByRole();
        Map<JobOfferStatus, Long> offersByStatus = queries.offersByStatus();
        Map<ApplicationStatus, Long> applicationsByStatus = queries.applicationsByStatus();
        Map<CvParsingStatus, Long> cvsByStatus = queries.cvsByParsingStatus();
        ScoreStats scores = queries.storedScores();
        LocalDateTime from = firstDayOfPeriod();
        Map<YearMonth, Long> users = queries.usersByMonth(from);
        Map<YearMonth, Long> offers = queries.offersByMonth(from);
        Map<YearMonth, Long> applications = queries.applicationsByMonth(from);
        List<MonthlyActivity> activity = periodMonths().stream()
                .map(m -> new MonthlyActivity(m.toString(), users.getOrDefault(m, 0L), offers.getOrDefault(m, 0L),
                        applications.getOrDefault(m, 0L)))
                .toList();
        return new AdminDashboardResponse(EnumCounts.total(usersByRole), usersByRole, queries.countDisabledUsers(),
                queries.countCompanies(), queries.countRecruitersWithoutCompany(),
                EnumCounts.total(offersByStatus), offersByStatus, queries.publishedOffersByContract(),
                EnumCounts.total(applicationsByStatus), applicationsByStatus,
                EnumCounts.total(cvsByStatus), cvsByStatus, scores.count(), round(scores.average()),
                queries.topRequestedSkills(TOP_SKILLS).stream()
                        .map(s -> new SkillCount(s.skill(), s.offers())).toList(),
                activity);
    }

    private static CvSummary cvSummary(CvRow cv) {
        CvState state = cv.status() == CvParsingStatus.TERMINE ? CvState.ANALYSE : CvState.IMPORTE;
        return new CvSummary(state, cv.status(), cv.updatedAt(), cv.parsedAt(), cv.extractedSkills());
    }

    /** Les {@link #MONTHS} derniers mois (UTC), le mois en cours compris, du plus ancien au plus récent. */
    private static List<YearMonth> periodMonths() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        List<YearMonth> months = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }
        return months;
    }

    private static LocalDateTime firstDayOfPeriod() {
        return periodMonths().getFirst().atDay(1).atStartOfDay();
    }

    private static List<MonthlyCount> monthly(Map<YearMonth, Long> counts) {
        return periodMonths().stream().map(m -> new MonthlyCount(m.toString(), counts.getOrDefault(m, 0L))).toList();
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
