package com.timetablingapp.lecturer;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "lecturers")
@SQLDelete(sql = "UPDATE lecturers SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Lecturer extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String nik;                 // logical unique (indexed in DB, not a UNIQUE constraint)

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String name;

    /** NOT NULL in the schema — must always be set. FK-like reference to a jurusan/faculty id. */
    @NotNull
    @Column(name = "home_base", nullable = false)
    private Integer homeBase;

    @Column(columnDefinition = "TEXT")
    private String alias;
}
