package com.gatepay.userservice.repository;

import com.gatepay.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    Optional<User> findWithRolesByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles"})
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u")
    Page<User> findAllWithRoles(Pageable pageable);


    @Query("""
    SELECT u FROM User u
    WHERE 
        LOWER(u.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR u.phoneNumber LIKE CONCAT('%', :keyword, '%')
     """)
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

}
