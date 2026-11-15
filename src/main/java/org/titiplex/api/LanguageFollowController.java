package org.titiplex.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.service.LanguageFollowService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/languages")
public class LanguageFollowController {

    private final LanguageFollowService followService;

    public LanguageFollowController(LanguageFollowService followService) {
        this.followService = followService;
    }

    /** POST /api/languages/{id}/follow — toggle follow */
    @PostMapping("/{id}/follow")
    public ResponseEntity<Map<String, Object>> toggleFollow(
            @PathVariable("id") String languageId,
            Authentication authentication
    ) {
        boolean following = followService.toggleFollow(languageId, authentication);
        return ResponseEntity.ok(Map.of(
                "languageId", languageId,
                "following", following,
                "followerCount", followService.countFollowers(languageId)
        ));
    }

    /** GET /api/languages/{id}/follow — check follow status */
    @GetMapping("/{id}/follow")
    public ResponseEntity<Map<String, Object>> getFollowStatus(
            @PathVariable("id") String languageId,
            Authentication authentication
    ) {
        boolean following = followService.isFollowing(languageId, authentication);
        return ResponseEntity.ok(Map.of(
                "languageId", languageId,
                "following", following,
                "followerCount", followService.countFollowers(languageId)
        ));
    }

    /** GET /api/languages/followed — list followed language IDs for current user */
    @GetMapping("/followed")
    public ResponseEntity<List<String>> getFollowedLanguages(Authentication authentication) {
        return ResponseEntity.ok(followService.getFollowedLanguageIds(authentication));
    }
}