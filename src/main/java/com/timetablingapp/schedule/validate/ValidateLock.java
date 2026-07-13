package com.timetablingapp.schedule.validate;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Table `validate_lock` — a single-row dirty flag. Laravel id() → BIGINT (Long).
 * Timestamps + soft-delete. lock=true means slot_acts is stale.
 */
@Entity
@Table(name = "validate_lock")
@SQLDelete(sql = "UPDATE validate_lock SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ValidateLock extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                 // Laravel id() → BIGINT

    /** true = master data changed, slot_acts stale; false = fresh. */
    @Column(nullable = false)
    private Boolean lock = false;

    public ValidateLock(Boolean lock) {
        this.lock = lock;
    }
}
