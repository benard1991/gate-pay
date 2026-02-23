package com.gatepay.userservice.repository;

import com.gatepay.userservice.enums.RoleEnum;
import com.gatepay.userservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleEnum name);

    Set<Role> findByNameIn(Set<RoleEnum> names);
}
