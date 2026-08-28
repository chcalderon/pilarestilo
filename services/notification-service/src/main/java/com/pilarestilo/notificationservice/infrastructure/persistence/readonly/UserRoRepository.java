package com.pilarestilo.notificationservice.infrastructure.persistence.readonly;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserRoRepository extends JpaRepository<UserRoEntity, UUID> {

    @Query("SELECT u FROM UserRoEntity u WHERE u.role IN :roles AND u.active = true")
    List<UserRoEntity> findActiveByRoleIn(@Param("roles") List<String> roles);
}
