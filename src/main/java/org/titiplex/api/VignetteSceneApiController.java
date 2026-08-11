package org.titiplex.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.titiplex.api.dto.VignetteSceneDto;
import org.titiplex.service.UserService;
import org.titiplex.service.VignetteSceneService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vignette-scenes")
public class VignetteSceneApiController {

    private final VignetteSceneService sceneService;
    private final UserService userService;

    public VignetteSceneApiController(VignetteSceneService sceneService, UserService userService) {
        this.sceneService = sceneService;
        this.userService = userService;
    }

    @GetMapping
    public List<VignetteSceneDto> list(Authentication auth) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        return sceneService.listByUser(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VignetteSceneDto create(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        return sceneService.create(userId, body.get("name"), body.get("sceneJson"));
    }

    @PatchMapping("/{id}")
    public VignetteSceneDto update(@PathVariable Long id,
                                   @RequestBody Map<String, String> body,
                                   Authentication auth) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        return sceneService.update(id, userId, body.get("name"), body.get("sceneJson"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        Long userId = userService.getUserByUsername(auth.getName()).getId();
        sceneService.delete(id, userId);
    }
}
