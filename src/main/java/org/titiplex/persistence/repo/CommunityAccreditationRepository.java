package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.AccreditationPermissionType;
import org.titiplex.persistence.model.AccreditationScope;
import org.titiplex.persistence.model.AccreditationScopeType;
import org.titiplex.persistence.model.CommunityAccreditation;

import java.util.List;
import java.util.Optional;

public interface CommunityAccreditationRepository extends JpaRepository<CommunityAccreditation, Long> {
    List<CommunityAccreditation> findByPermissionTypeAndScopeTypeAndTargetIdOrderByGrantedAtDesc(
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType,
            String targetId
    );

    List<CommunityAccreditation> findByPermissionTypeAndScopeTypeOrderByGrantedAtDesc(
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType
    );

    Optional<CommunityAccreditation> findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
            Long userId,
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType,
            String targetId
    );

    boolean existsByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
            Long userId,
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType,
            String targetId
    );

    /** Grants for a scope, newest first (a GLOBAL scope ignores the target). */
    default List<CommunityAccreditation> listByScope(AccreditationScope scope) {
        return scope.isGlobal()
                ? findByPermissionTypeAndScopeTypeOrderByGrantedAtDesc(scope.permissionType(), scope.scopeType())
                : findByPermissionTypeAndScopeTypeAndTargetIdOrderByGrantedAtDesc(
                scope.permissionType(), scope.scopeType(), scope.targetId());
    }

    default Optional<CommunityAccreditation> findGrant(Long userId, AccreditationScope scope) {
        return findByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                userId, scope.permissionType(), scope.scopeType(), scope.targetId());
    }

    default boolean hasGrant(Long userId, AccreditationScope scope) {
        return existsByUserIdAndPermissionTypeAndScopeTypeAndTargetId(
                userId, scope.permissionType(), scope.scopeType(), scope.targetId());
    }
}
