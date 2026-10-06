package com.smartrecruit.dashboard.repository;

import com.smartrecruit.application.entity.ApplicationStatus;
import com.smartrecruit.cv.entity.CvParsingStatus;
import com.smartrecruit.joboffer.entity.ContractType;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.user.entity.RoleName;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Requêtes d'agrégation des tableaux de bord : comptages et moyennes calculés par la base (group by, count, avg),
 * sans charger les entités ni leurs relations. Chaque méthode exécute une seule requête.
 */
@Repository
public class DashboardQueryRepository {

    /** Scores visibles par le candidat : offre publiée, ou offre à laquelle il a postulé. */
    private static final String VISIBLE_MATCHES = """
            from MatchingResult m join m.jobOffer o
            where m.candidate.id = :candidateId
              and (o.status = :published
                   or exists (select a.id from Application a where a.candidate.id = :candidateId and a.jobOffer = o))
            """;

    /** Candidatures aux offres d'une entreprise ayant un score de compatibilité. */
    private static final String SCORED_APPLICATIONS = """
            from Application a join MatchingResult m on m.candidate = a.candidate and m.jobOffer = a.jobOffer
            where a.jobOffer.company.id = :companyId
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public record CvRow(CvParsingStatus status, LocalDateTime updatedAt, LocalDateTime parsedAt, int extractedSkills) {
    }

    public record MatchRow(Long jobOfferId, String title, String companyName, JobOfferStatus offerStatus,
                           BigDecimal score, LocalDateTime computedAt, boolean applied) {
    }

    public record OfferRow(Long id, String title, JobOfferStatus status, LocalDateTime createdAt) {
    }

    public record ScoreStats(long count, Double average, BigDecimal best) {
    }

    public record CandidateRow(Long applicationId, String firstName, String lastName, Long jobOfferId,
                               String jobTitle, BigDecimal score, ApplicationStatus status, LocalDateTime computedAt,
                               CvParsingStatus cvStatus, LocalDateTime cvParsedAt) {
    }

    public record SkillRow(String skill, long offers) {
    }

    // ---------- Candidat ----------

    public Map<ApplicationStatus, Long> applicationsByStatusOfCandidate(Long candidateId) {
        return EnumCounts.of(ApplicationStatus.class, rows("""
                select a.status, count(a) from Application a where a.candidate.id = :candidateId group by a.status
                """, Map.of("candidateId", candidateId)));
    }

    public long countPublishedOffersNotAppliedBy(Long candidateId) {
        return count("""
                select count(o) from JobOffer o where o.status = :published
                  and not exists (select a.id from Application a where a.jobOffer = o and a.candidate.id = :candidateId)
                """, Map.of("published", JobOfferStatus.PUBLIEE, "candidateId", candidateId));
    }

    public Optional<CvRow> cvOf(Long candidateId) {
        return entityManager.createQuery("""
                        select new com.smartrecruit.dashboard.repository.DashboardQueryRepository$CvRow(
                            c.parsingStatus, c.updatedAt, c.parsedAt, size(c.extractedSkills))
                        from Cv c where c.candidate.id = :candidateId
                        """, CvRow.class)
                .setParameter("candidateId", candidateId)
                .getResultStream().findFirst();
    }

    public long countVisibleMatches(Long candidateId) {
        return count("select count(m) " + VISIBLE_MATCHES,
                Map.of("candidateId", candidateId, "published", JobOfferStatus.PUBLIEE));
    }

    public List<MatchRow> topVisibleMatches(Long candidateId, int limit) {
        TypedQuery<MatchRow> query = entityManager.createQuery("""
                select new com.smartrecruit.dashboard.repository.DashboardQueryRepository$MatchRow(
                    o.id, o.title, o.company.name, o.status, m.score, m.computedAt,
                    exists (select a.id from Application a where a.candidate.id = :candidateId and a.jobOffer = o))
                """ + VISIBLE_MATCHES + " order by m.score desc, m.computedAt desc, o.id desc", MatchRow.class);
        return query.setParameter("candidateId", candidateId)
                .setParameter("published", JobOfferStatus.PUBLIEE)
                .setMaxResults(limit)
                .getResultList();
    }

    // ---------- Recruteur (offres d'une entreprise) ----------

    public List<OfferRow> offersOfCompany(Long companyId) {
        return entityManager.createQuery("""
                        select new com.smartrecruit.dashboard.repository.DashboardQueryRepository$OfferRow(
                            o.id, o.title, o.status, o.createdAt)
                        from JobOffer o where o.company.id = :companyId order by o.createdAt desc, o.id desc
                        """, OfferRow.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    /** Nombre de candidatures par offre et par statut : {@code [offreId, statut, nombre]}. */
    public List<Object[]> applicationsByOfferAndStatus(Long companyId) {
        return rows("""
                select a.jobOffer.id, a.status, count(a) from Application a
                where a.jobOffer.company.id = :companyId group by a.jobOffer.id, a.status
                """, Map.of("companyId", companyId));
    }

    public Map<Long, ScoreStats> scoresByOffer(Long companyId) {
        Map<Long, ScoreStats> stats = new HashMap<>();
        for (Object[] row : rows("select a.jobOffer.id, count(m), avg(m.score), max(m.score) " + SCORED_APPLICATIONS
                + " group by a.jobOffer.id", Map.of("companyId", companyId))) {
            stats.put((Long) row[0], scoreStats(row[1], row[2], row[3]));
        }
        return stats;
    }

    public ScoreStats scoresOfCompany(Long companyId) {
        Object[] row = rows("select count(m), avg(m.score), max(m.score) " + SCORED_APPLICATIONS,
                Map.of("companyId", companyId)).getFirst();
        return scoreStats(row[0], row[1], row[2]);
    }

    public Map<YearMonth, Long> applicationsByMonthOfCompany(Long companyId, LocalDateTime from) {
        return byMonth("Application e", "e.jobOffer.company.id = :companyId and ",
                Map.of("companyId", companyId, "from", from));
    }

    public List<CandidateRow> topCandidates(Long companyId, int limit) {
        return entityManager.createQuery("""
                        select new com.smartrecruit.dashboard.repository.DashboardQueryRepository$CandidateRow(
                            a.id, u.firstName, u.lastName, o.id, o.title, m.score, a.status, m.computedAt,
                            cv.parsingStatus, cv.parsedAt)
                        from Application a join a.jobOffer o join a.candidate p join p.user u
                          join MatchingResult m on m.candidate = p and m.jobOffer = o
                          left join p.cv cv
                        where o.company.id = :companyId
                        order by m.score desc, a.createdAt desc, a.id desc
                        """, CandidateRow.class)
                .setParameter("companyId", companyId)
                .setMaxResults(limit)
                .getResultList();
    }

    // ---------- Administrateur (plateforme) ----------

    public Map<RoleName, Long> usersByRole() {
        return EnumCounts.of(RoleName.class, rows("select u.role.name, count(u) from User u group by u.role.name",
                Map.of()));
    }

    public long countDisabledUsers() {
        return count("select count(u) from User u where u.enabled = false", Map.of());
    }

    public long countCompanies() {
        return count("select count(c) from Company c", Map.of());
    }

    public long countRecruitersWithoutCompany() {
        return count("select count(r) from RecruiterProfile r where r.company is null", Map.of());
    }

    public Map<JobOfferStatus, Long> offersByStatus() {
        return EnumCounts.of(JobOfferStatus.class, rows("select o.status, count(o) from JobOffer o group by o.status",
                Map.of()));
    }

    public long countOffers(JobOfferStatus status) {
        return count("select count(o) from JobOffer o where o.status = :status", Map.of("status", status));
    }

    public Map<ContractType, Long> publishedOffersByContract() {
        return EnumCounts.of(ContractType.class, rows("""
                select o.contractType, count(o) from JobOffer o where o.status = :published group by o.contractType
                """, Map.of("published", JobOfferStatus.PUBLIEE)));
    }

    public Map<ApplicationStatus, Long> applicationsByStatus() {
        return EnumCounts.of(ApplicationStatus.class, rows(
                "select a.status, count(a) from Application a group by a.status", Map.of()));
    }

    public Map<CvParsingStatus, Long> cvsByParsingStatus() {
        return EnumCounts.of(CvParsingStatus.class, rows(
                "select c.parsingStatus, count(c) from Cv c group by c.parsingStatus", Map.of()));
    }

    public ScoreStats storedScores() {
        Object[] row = rows("select count(m), avg(m.score), max(m.score) from MatchingResult m", Map.of()).getFirst();
        return scoreStats(row[0], row[1], row[2]);
    }

    public List<SkillRow> topRequestedSkills(int limit) {
        return entityManager.createQuery("""
                        select new com.smartrecruit.dashboard.repository.DashboardQueryRepository$SkillRow(
                            s.name, count(o))
                        from JobOffer o join o.requiredSkills s where o.status = :published
                        group by s.name order by count(o) desc, s.name
                        """, SkillRow.class)
                .setParameter("published", JobOfferStatus.PUBLIEE)
                .setMaxResults(limit)
                .getResultList();
    }

    public Map<YearMonth, Long> usersByMonth(LocalDateTime from) {
        return byMonth("User e", "", Map.of("from", from));
    }

    public Map<YearMonth, Long> offersByMonth(LocalDateTime from) {
        return byMonth("JobOffer e", "", Map.of("from", from));
    }

    public Map<YearMonth, Long> applicationsByMonth(LocalDateTime from) {
        return byMonth("Application e", "", Map.of("from", from));
    }

    // ---------- Utilitaires ----------

    /** Créations par mois depuis {@code from} ; {@code entity} est aliasé {@code e}, {@code filter} se termine par and. */
    private Map<YearMonth, Long> byMonth(String entity, String filter, Map<String, Object> parameters) {
        Map<YearMonth, Long> counts = new HashMap<>();
        for (Object[] row : rows("select extract(year from e.createdAt), extract(month from e.createdAt), count(e) from "
                + entity + " where " + filter + "e.createdAt >= :from"
                + " group by extract(year from e.createdAt), extract(month from e.createdAt)", parameters)) {
            counts.put(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                    ((Number) row[2]).longValue());
        }
        return counts;
    }

    private List<Object[]> rows(String jpql, Map<String, Object> parameters) {
        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }

    private long count(String jpql, Map<String, Object> parameters) {
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        parameters.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private static ScoreStats scoreStats(Object count, Object average, Object best) {
        return new ScoreStats(((Number) count).longValue(), average == null ? null : ((Number) average).doubleValue(),
                (BigDecimal) best);
    }
}
