package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, String> {
    @EntityGraph(value = "user-with-roles")
    Optional<User> findUserByUsername(String username);

    @Query("SELECT u.username FROM users u WHERE u.username IN (SELECT DISTINCT o.user.username FROM orders o)")
    List<String> getAllUsernamesHavingOrders();
}
