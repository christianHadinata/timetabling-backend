package com.timetablingapp.jurusan.konsentrasi;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.jurusan.Jurusan;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "konsentrasi")
@SQLDelete(sql = "UPDATE konsentrasi SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Konsentrasi extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurusan_id", nullable = false)
    private Jurusan jurusan;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String konsentrasi;
}
