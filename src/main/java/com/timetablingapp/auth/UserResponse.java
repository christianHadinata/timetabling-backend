package com.timetablingapp.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String faculty;
    private LocalDateTime emailVerifiedAt;
    private boolean admin;

    /**
     * Factory method to convert a User entity to UserResponse.
     */
    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .faculty(user.getFaculty())
                .emailVerifiedAt(user.getEmailVerifiedAt())
                .admin(user.isAdmin())
                .build();
    }
}
