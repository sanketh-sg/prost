package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BottlesRepository extends JpaRepository<Bottle, Long> {

    @Override
    Optional<Bottle> findById(@NotNull Long id);

    @Override
    boolean existsById(@NotNull Long id);

    @Override
    Page<Bottle> findAll(Pageable pageable);

    @Query("SELECT b FROM bottles b WHERE b.inStock > 0")
    Page<Bottle> findAllAvailable(Pageable pageable);

    @Query("SELECT b FROM bottles b WHERE b.volumePercent > 0.0")
    Page<Bottle> findAllAlcoholic(Pageable pageable);

    @Query("SELECT b FROM bottles b WHERE b.volumePercent = 0.0")
    Page<Bottle> findAllNonAlcoholic(Pageable pageable);

    @Query("SELECT count(b) FROM bottles b WHERE b.volumePercent > 0.0")
    Long countAllAlcoholic();

    @Query("SELECT count(b) FROM bottles b WHERE b.volumePercent <= 0.0")
    Long countAllNonAlcoholic();

    @Query("SELECT b FROM bottles b WHERE b.volumePercent > 3 AND b.volumePercent < 8")
    List<Bottle> findAllBeerLike();
}
