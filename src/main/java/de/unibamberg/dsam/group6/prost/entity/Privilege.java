package de.unibamberg.dsam.group6.prost.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "privileges")
@Getter
@Setter
@NoArgsConstructor
public class Privilege implements Serializable {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String name;

    @ManyToMany(mappedBy = "privileges")
    private Set<Role> roles;
}
