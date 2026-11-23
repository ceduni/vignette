# `persistence/repo/` — Spring Data repositories

One `JpaRepository` per entity. Queries are derived from method names when they stay short, or
written in JPQL with `@Query`.

Keep public methods to **2–3 parameters**: group related values in a record and expose a `default`
method that calls the long derived query. Example: `CommunityAccreditationRepository.hasGrant(userId,
AccreditationScope)`.

Locking helpers (`lockById`) take a row lock for the current transaction; services use them to
serialize concurrent changes (e.g. two uploads computing the next scene index).
