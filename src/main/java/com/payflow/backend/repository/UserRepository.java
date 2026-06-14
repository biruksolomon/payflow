package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// ==================== USER REPOSITORY ====================
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndIsDeletedFalse(String email);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isDeleted = false")
    Optional<User> findActiveByEmail(@Param("email") String email);

    boolean existsByEmail(String email);

    List<User> findByUserRoleAndIsDeletedFalse(UserRole userRole);

    @Query("SELECT u FROM User u WHERE u.id = :userId AND u.isDeleted = false")
    Optional<User> findActiveById(@Param("userId") Long userId);
}