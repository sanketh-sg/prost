package de.unibamberg.dsam.group6.prost.repository;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeveragesRepository extends JpaRepository<Beverage, Long> {}
