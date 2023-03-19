package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdersRepository extends JpaRepository<Order, Long> {
    @EntityGraph(value = "order-beverages")
    List<Order> findAllByUser_username(String username);

    @EntityGraph(value = "order-beverages")
    Optional<Order> findByUser_usernameAndId(String username, Long id);
}
