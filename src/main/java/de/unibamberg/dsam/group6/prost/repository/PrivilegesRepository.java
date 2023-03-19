package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Privilege;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivilegesRepository extends JpaRepository<Privilege, Long> {
    @Override
    Optional<Privilege> findById(Long aLong);

    Optional<Privilege> findByName(String name);
}
