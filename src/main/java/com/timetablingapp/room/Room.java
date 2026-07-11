package com.timetablingapp.room;

import com.timetablingapp.common.base.BaseSoftDeleteEntity;
import com.timetablingapp.room.type.RoomType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "rooms")
@SQLDelete(sql = "UPDATE rooms SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Room extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank
    @Column(name = "room_code", nullable = false, length = 20)
    private String roomCode;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "unit_owner", columnDefinition = "TEXT", nullable = false)
    private String unitOwner;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String location;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String building;

    @NotBlank
    @Column(length = 20, nullable = false)
    private String floor;

    @NotNull
    @Column(nullable = false)
    private Integer capacity;

    /** Self-referential parent. Child rooms are physically inside the parent
     *  and mutually block it during scheduling (used in Phase 7). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_room_id")
    private Room parentRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(length = 255)
    private String virtual;
}
