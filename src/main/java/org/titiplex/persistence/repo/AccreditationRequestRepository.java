package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.AccreditationPermissionType;
import org.titiplex.persistence.model.AccreditationRequest;
import org.titiplex.persistence.model.AccreditationScope;
import org.titiplex.persistence.model.AccreditationRequestStatus;
import org.titiplex.persistence.model.AccreditationScopeType;

import java.util.List;

public interface AccreditationRequestRepository extends JpaRepository<AccreditationRequest, Long> {
    List<AccreditationRequest> findByPermissionTypeAndScopeTypeAndTargetIdOrderByCreatedAtDesc(
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType,
            String targetId
    );

    List<AccreditationRequest> findByPermissionTypeAndScopeTypeOrderByCreatedAtDesc(
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType
    );

    boolean existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
            Long requestedByUserId,
            AccreditationPermissionType permissionType,
            AccreditationScopeType scopeType,
            String targetId,
            AccreditationRequestStatus status
    );

    /** Requests for a scope, newest first (a GLOBAL scope ignores the target). */
    default List<AccreditationRequest> listByScope(AccreditationScope scope) {
        return scope.isGlobal()
                ? findByPermissionTypeAndScopeTypeOrderByCreatedAtDesc(scope.permissionType(), scope.scopeType())
                : findByPermissionTypeAndScopeTypeAndTargetIdOrderByCreatedAtDesc(
                scope.permissionType(), scope.scopeType(), scope.targetId());
    }

    /** True when the user already has a pending request for this scope. */
    default boolean existsPending(Long userId, AccreditationScope scope) {
        return existsByRequestedByUserIdAndPermissionTypeAndScopeTypeAndTargetIdAndStatus(
                userId, scope.permissionType(), scope.scopeType(), scope.targetId(), AccreditationRequestStatus.PENDING);
    }
}
