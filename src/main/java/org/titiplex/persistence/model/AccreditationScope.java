package org.titiplex.persistence.model;

/** What an accreditation applies to: a permission, over a scope type, on an optional target (null for GLOBAL). */
public record AccreditationScope(AccreditationPermissionType permissionType,
                                 AccreditationScopeType scopeType,
                                 String targetId) {

    public boolean isGlobal() {
        return scopeType == AccreditationScopeType.GLOBAL;
    }
}
