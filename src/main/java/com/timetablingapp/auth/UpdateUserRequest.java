package com.timetablingapp.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /**
     * Optional. If provided and non-empty, the password will be updated.
     * If null or empty, the existing password is kept.
     * Mirrors Laravel: if($req['password']){ Hash::make(...) } else { unset(...) }
     */
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Faculty name. Null or empty = ADMIN user.
     */
    private String faculty;
}
