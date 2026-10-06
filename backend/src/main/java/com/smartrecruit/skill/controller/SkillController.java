package com.smartrecruit.skill.controller;

import com.smartrecruit.skill.dto.SkillResponse;
import com.smartrecruit.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Autocomplétion des compétences (utilisateur connecté). */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    public List<SkillResponse> search(@RequestParam(required = false) String query,
                                      @RequestParam(defaultValue = "20") int limit) {
        return skillService.search(query, limit);
    }
}
