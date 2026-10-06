package com.smartrecruit.chatbot.service;

import com.smartrecruit.ai.AiServiceClient;
import com.smartrecruit.ai.AiServiceUnavailableException;
import com.smartrecruit.chatbot.dto.AiChatbotRequest;
import com.smartrecruit.chatbot.dto.AiChatbotResponse;
import com.smartrecruit.chatbot.dto.ChatbotOfferSummary;
import com.smartrecruit.chatbot.dto.ChatbotQuestionRequest;
import com.smartrecruit.chatbot.dto.ChatbotReply;
import com.smartrecruit.joboffer.entity.JobOffer;
import com.smartrecruit.joboffer.entity.JobOfferStatus;
import com.smartrecruit.joboffer.repository.JobOfferRepository;
import com.smartrecruit.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chatbot RH : question du candidat -> Spring Boot (catalogue des offres publiées) -> FastAPI (module chatbot)
 * -> réponse validée -> Angular.
 *
 * <ul>
 *   <li>Le service IA reçoit la question et des données publiques sur les offres publiées (titre, entreprise,
 *       ville, contrat, expérience, niveau, salaire, compétences) : rien sur le candidat.</li>
 *   <li>Une réponse citant une offre absente du catalogue envoyé est rejetée (503) : le chatbot ne peut pas
 *       inventer d'offre. Les offres affichées sont reconstruites à partir de la base.</li>
 *   <li>Les questions ne sont ni enregistrées ni journalisées (seuls l'intention et des compteurs le sont).</li>
 * </ul>
 */
@Slf4j
@Service
public class ChatbotService {

    static final String ASK_PATH = "/api/chatbot/ask";
    static final int MAX_CATALOGUE = 200;

    private final JobOfferRepository jobOfferRepository;
    private final AiServiceClient aiClient;
    private final TransactionTemplate readOnlyTransaction;

    public ChatbotService(JobOfferRepository jobOfferRepository, AiServiceClient aiClient,
                          PlatformTransactionManager transactionManager) {
        this.jobOfferRepository = jobOfferRepository;
        this.aiClient = aiClient;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    public ChatbotReply ask(ChatbotQuestionRequest question) {
        Catalogue catalogue = readOnlyTransaction.execute(status -> catalogue(question.jobOfferId()));
        AiChatbotRequest request = new AiChatbotRequest(question.message().strip(), catalogue.contextOfferId(),
                catalogue.offers());
        AiChatbotResponse response = aiClient.post(ASK_PATH, request, AiChatbotResponse.class);
        checkOffersExist(response, catalogue.summaries());
        log.info("Chatbot : intention {}, {} offre(s) citée(s) sur {} envoyée(s)", response.intent(),
                response.offerIds().size(), catalogue.offers().size());
        Map<Long, ChatbotOfferSummary> summaries = catalogue.summaries();
        return new ChatbotReply(
                response.intent(),
                response.answer(),
                response.confidence(),
                response.offerIds().stream().map(summaries::get).toList(),
                Math.max(response.totalMatches(), response.offerIds().size()),
                response.contextOfferId() == null ? null : summaries.get(response.contextOfferId()),
                response.entities(),
                response.suggestions(),
                response.actions(),
                response.chatbotVersion());
    }

    /**
     * Les {@value #MAX_CATALOGUE} offres publiées les plus récentes, plus l'offre de contexte si elle est publiée
     * (une offre de contexte en brouillon, fermée ou inexistante est ignorée).
     */
    private Catalogue catalogue(Long contextOfferId) {
        List<Long> ids = new ArrayList<>(jobOfferRepository.findIdsByStatus(JobOfferStatus.PUBLIEE,
                PageRequest.of(0, MAX_CATALOGUE)));
        Long context = null;
        if (contextOfferId != null) {
            boolean published = ids.contains(contextOfferId) || jobOfferRepository.findById(contextOfferId)
                    .filter(o -> o.getStatus() == JobOfferStatus.PUBLIEE).isPresent();
            if (published) {
                context = contextOfferId;
                if (!ids.contains(contextOfferId)) {
                    ids.add(contextOfferId);
                }
            }
        }
        Map<Long, JobOffer> byId = jobOfferRepository.findWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(JobOffer::getId, Function.identity()));
        List<AiChatbotRequest.CatalogOffer> offers = new ArrayList<>();
        Map<Long, ChatbotOfferSummary> summaries = new LinkedHashMap<>();
        for (Long id : ids) {
            JobOffer offer = byId.get(id);
            if (offer == null) {
                continue;
            }
            List<String> skills = SkillService.names(offer.getRequiredSkills());
            offers.add(new AiChatbotRequest.CatalogOffer(offer.getId(), offer.getTitle(), offer.getCompany().getName(),
                    offer.getLocation(), offer.getContractType(), offer.getMinExperienceYears(),
                    offer.getEducationLevel(), offer.getSalaryMin(), offer.getSalaryMax(), skills));
            summaries.put(offer.getId(), new ChatbotOfferSummary(offer.getId(), offer.getTitle(),
                    offer.getCompany().getName(), offer.getLocation(), offer.getContractType(),
                    offer.getMinExperienceYears(), offer.getEducationLevel(), skills));
        }
        return new Catalogue(offers, summaries, summaries.containsKey(context) ? context : null);
    }

    /** Toute offre citée doit faire partie du catalogue envoyé ; sinon la réponse est rejetée. */
    private static void checkOffersExist(AiChatbotResponse response, Map<Long, ChatbotOfferSummary> summaries) {
        boolean unknownOffer = response.offerIds().stream().anyMatch(id -> !summaries.containsKey(id))
                || (response.contextOfferId() != null && !summaries.containsKey(response.contextOfferId()))
                || response.offerIds().stream().distinct().count() != response.offerIds().size();
        if (unknownOffer) {
            log.error("Chatbot : réponse rejetée, elle cite une offre absente du catalogue envoyé");
            throw new AiServiceUnavailableException(AiServiceUnavailableException.Reason.INVALID_RESPONSE);
        }
    }

    private record Catalogue(List<AiChatbotRequest.CatalogOffer> offers, Map<Long, ChatbotOfferSummary> summaries,
                             Long contextOfferId) {
    }
}
