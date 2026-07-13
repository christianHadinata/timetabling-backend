package com.timetablingapp.schedule.validate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dirty-flag semantics ported from Laravel ValidateLockRepository:
 *   check() -> isStale(), lock() -> lock(), open() -> open().
 * lock=true means the master data changed since the last revalidation, so
 * slot_acts is stale and the UI should prompt the admin to re-run validation.
 */
@Service
@RequiredArgsConstructor
public class ValidateLockService {

    private final ValidateLockRepository repository;

    /** True when slot_acts is stale (latest row exists and lock = true). */
    @Transactional(readOnly = true)
    public boolean isStale() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(ValidateLock::getLock)
                .orElse(false);
    }

    /** Mark stale — called after any master-data mutation. Upserts the latest row. */
    @Transactional
    public void lock() {
        setLock(true);
    }

    /** Mark fresh — called at the end of a successful revalidate. */
    @Transactional
    public void open() {
        setLock(false);
    }

    private void setLock(boolean value) {
        ValidateLock row = repository.findFirstByOrderByCreatedAtDesc()
                .orElseGet(ValidateLock::new);
        row.setLock(value);
        repository.save(row);   // insert when new, update otherwise (mirrors PHP create/update)
    }
}
