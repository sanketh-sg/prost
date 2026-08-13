package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeveragesRepository extends JpaRepository<Beverage, Long> {

    /**
     * Loads a beverage with a write lock held until the transaction commits.
     *
     * <p>Checkout must not merely re-read stock: at the default isolation level two
     * transactions can both read "2 in stock" before either writes, and both then
     * pass their own check. The lock serialises concurrent checkouts per beverage,
     * so the second one sees the first one's decrement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Beverage b where b.id = :id")
    Optional<Beverage> findByIdForUpdate(@Param("id") Long id);
}
