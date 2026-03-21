package com.openmanus.saa.controller;

import com.openmanus.saa.model.SkillInfoResponse;
import com.openmanus.saa.service.SkillsService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillsService skillsService;

    public SkillsController(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    @GetMapping
    public List<SkillInfoResponse> listSkills() {
        return skillsService.listSkills();
    }

    @GetMapping("/{skillName}")
    public ResponseEntity<SkillInfoResponse> getSkill(@PathVariable String skillName) {
        return skillsService.getSkill(skillName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{skillName}/content")
    public Map<String, String> readSkill(@PathVariable String skillName) {
        try {
            return Map.of(
                    "skillName", skillName,
                    "content", skillsService.readSkill(skillName)
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @GetMapping("/instructions/load")
    public Map<String, String> getLoadInstructions() {
        return Map.of("instructions", skillsService.getLoadInstructions());
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        int count = skillsService.reload();
        return Map.of(
                "success", true,
                "count", count
        );
    }
}
