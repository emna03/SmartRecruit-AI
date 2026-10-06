package com.smartrecruit.skill.service;

import com.smartrecruit.skill.dto.SkillResponse;
import com.smartrecruit.skill.entity.Skill;
import com.smartrecruit.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Référentiel de compétences partagé. Une compétence saisie ("java", " Java ") est rattachée
 * à l'entrée existante sans tenir compte de la casse ; sinon elle est créée.
 */
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;

    @Transactional
    public Set<Skill> resolve(Collection<String> names) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : names) {
            String name = normalize(raw);
            if (!name.isEmpty()) {
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        Set<Skill> skills = new LinkedHashSet<>();
        for (String name : unique.values()) {
            skills.add(skillRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> skillRepository.save(new Skill(name, null))));
        }
        return skills;
    }

    /**
     * Compétences détectées automatiquement (nom -> catégorie du lexique) : rattachées au référentiel sans tenir
     * compte de la casse, créées avec leur catégorie, ou complétées si la catégorie existante est vide.
     */
    @Transactional
    public Set<Skill> resolveCategorized(Map<String, String> categoriesByName) {
        Set<Skill> skills = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        categoriesByName.forEach((raw, category) -> {
            String name = normalize(raw);
            if (name.isEmpty() || !seen.add(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            Skill skill = skillRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> skillRepository.save(new Skill(name, category)));
            if (skill.getCategory() == null && category != null) {
                skill.setCategory(category);
            }
            skills.add(skill);
        });
        return skills;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> search(String query, int limit) {
        int size = Math.clamp(limit, 1, 50);
        List<Skill> skills = (query == null || query.isBlank())
                ? skillRepository.findAll(PageRequest.of(0, size, Sort.by("name"))).getContent()
                : skillRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim(), PageRequest.of(0, size));
        return skills.stream().map(SkillResponse::from).toList();
    }

    public static List<String> names(Collection<Skill> skills) {
        return skills.stream().map(Skill::getName).sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT))).toList();
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
