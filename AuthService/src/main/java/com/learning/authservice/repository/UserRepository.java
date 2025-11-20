package com.learning.authservice.repository;

import com.learning.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
	Optional<User> findByEmail(String email);
	boolean existsByEmail(String email);
	
	@Modifying
	@Transactional
	@Query(value = "UPDATE AppUser SET role = :role WHERE email = :email", nativeQuery = true)
	void updateUserRole(@Param("email") String email, @Param("role") String role );
}
