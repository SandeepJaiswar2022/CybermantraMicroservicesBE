package com.learning.authservice.Enum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.learning.authservice.Enum.Permission.*;
@Getter
@RequiredArgsConstructor
public enum Role {
	STUDENT(
			Set.of(
					STUDENT_CREATE,
                    STUDENT_DELETE,
                    STUDENT_UPDATE,
                    STUDENT_READ
                   )
    ),
	INSTRUCTOR(
			Set.of(
					INSTRUCTOR_CREATE,
					INSTRUCTOR_DELETE,
					INSTRUCTOR_UPDATE,
					INSTRUCTOR_READ
			)
	),

    ADMIN(
            Set.of(
                    ADMIN_DELETE,
                    ADMIN_READ,
                    ADMIN_CREATE,
                    ADMIN_UPDATE,

                    STUDENT_CREATE,
                    STUDENT_DELETE,
                    STUDENT_UPDATE,
                    STUDENT_READ
            )
    );
	
	private final Set<Permission> permissions;

	public List<SimpleGrantedAuthority> getGrantedAuthorities()
	{
		List<SimpleGrantedAuthority> authorities = getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
	}
}