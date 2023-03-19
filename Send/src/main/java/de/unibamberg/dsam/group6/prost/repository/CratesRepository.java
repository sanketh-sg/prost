package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Crate;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CratesRepository extends JpaRepository<Crate, Long> {

    @Override
    Optional<Crate> findById(@NotNull Long id);

    @Override
    @EntityGraph(value = "crate-with-bottle")
    Page<Crate> findAll(Pageable pageable);

    @Override
    boolean existsById(@NotNull Long id);
}
