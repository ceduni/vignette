package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.titiplex.persistence.model.ScenarioHistoryAction;
import org.titiplex.persistence.model.ScenarioHistoryEntry;
import org.titiplex.persistence.repo.ScenarioHistoryRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ScenarioHistoryService {

    private final ScenarioHistoryRepository repo;
    private final UserService userService;

    public ScenarioHistoryService(ScenarioHistoryRepository repo, UserService userService) {
        this.repo = repo;
        this.userService = userService;
    }

    public void record(Long scenarioId, Long actorUserId, ScenarioHistoryAction action, String summary) {
        ScenarioHistoryEntry entry = new ScenarioHistoryEntry();
        entry.setScenarioId(scenarioId);
        entry.setActorUserId(actorUserId);
        entry.setActorUsername(userService.getUserById(actorUserId).getUsername());
        entry.setAction(action);
        entry.setSummary(summary);
        entry.setCreatedAt(Instant.now());
        repo.save(entry);
    }

    public List<ScenarioHistoryEntry> list(Long scenarioId) {
        return repo.findByScenarioIdOrderByCreatedAtDesc(scenarioId);
    }
}
