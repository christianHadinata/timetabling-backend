package com.timetablingapp.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.timetablingapp.common.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "remember_token", length = 100)
    private String rememberToken;

    @Column
    private String faculty;

    /**
     * Determines if this user is an admin.
     * Admin is defined as a user with null or empty faculty.
     * Mirrors Laravel: User::isAdmin() → is_null($faculty) || $faculty == ""
     */
    @Transient
    public boolean isAdmin() {
        return faculty == null || faculty.isBlank();
    }

    /**
     * Returns the user's role based on their faculty field.
     */
    @Transient
    public UserRole getRole() {
        return isAdmin() ? UserRole.ADMIN : UserRole.FACULTY_USER;
    }
}
