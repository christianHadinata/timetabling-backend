package com.timetablingapp.jurusan;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "jurusans")
@SQLDelete(sql = "UPDATE jurusans SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Jurusan extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String faculty;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('D3','S1','S2','S3') DEFAULT 'S1'", nullable = false)
    private Jenjang jenjang;

    @Column(nullable = false)
    private Integer color;
}
