package org.titiplex.config.components;

import org.springframework.stereotype.Service;
import org.titiplex.api.security.ProtectedResource;

@Service
public class OwnershipSecurityServiceImpl implements OwnershipSecurityService {

    private final ScenarioSecurity scenarioSecurity;
    private final ThumbnailSecurity thumbnailSecurity;
    private final AudioSecurity audioSecurity;

    public OwnershipSecurityServiceImpl(
            ScenarioSecurity scenarioSecurity,
            ThumbnailSecurity thumbnailSecurity,
            AudioSecurity audioSecurity
    ) {
        this.scenarioSecurity = scenarioSecurity;
        this.thumbnailSecurity = thumbnailSecurity;
        this.audioSecurity = audioSecurity;
    }

    @Override
    public boolean isOwner(ProtectedResource resource, Object resourceId, String username) {
        Long id = toLong(resourceId);

        return switch (resource) {
            case SCENARIO -> scenarioSecurity.isOwner(id, username);
            case SCENARIO_AUTHOR_ONLY -> scenarioSecurity.isAuthor(id, username);
            case THUMBNAIL -> thumbnailSecurity.isOwner(id, username);
            case AUDIO -> audioSecurity.isOwner(id, username);
        };
    }

    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("Unsupported resource id type: " + value);
    }
}