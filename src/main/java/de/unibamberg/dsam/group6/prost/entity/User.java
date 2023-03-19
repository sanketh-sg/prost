package de.unibamberg.dsam.group6.prost.entity;

import de.unibamberg.dsam.group6.prost.util.annotation.IsAfter;
import java.time.LocalDate;
import java.util.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import lombok.*;
import org.hibernate.Hibernate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity(name = "users")
@NamedEntityGraph(
        name = "user-with-roles",
        attributeNodes = {
            @NamedAttributeNode(value = "roles", subgraph = "roles.privileges"),
            @NamedAttributeNode("billingAddress"),
            @NamedAttributeNode("deliveryAddress")
        },
        subgraphs = @NamedSubgraph(name = "roles.privileges", attributeNodes = @NamedAttributeNode("privileges")))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    @Id
    @NotEmpty
    @Column(name = "username", nullable = false)
    private String username;

    @NotEmpty
    @Column(name = "password", nullable = false)
    private String password;

    @NotNull
    @Column(name = "birthday", nullable = false)
    @Past
    @IsAfter(year = 1900)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    // region Relations

    @OneToMany(mappedBy = "user")
    private List<Order> orders;

    @ManyToOne
    @JoinColumn(name = "billing_address_id")
    private Address billingAddress;

    @ManyToOne
    @JoinColumn(name = "delivery_address_id")
    private Address deliveryAddress;

    // endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        User user = (User) o;
        return username != null && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        final var authorities = new HashSet<GrantedAuthority>();
        this.roles.stream().map(r -> new SimpleGrantedAuthority(r.getName())).forEach(authorities::add);
        this.roles.stream()
                .flatMap(r -> r.getPrivileges().stream())
                .map(r -> new SimpleGrantedAuthority(r.getName()))
                .forEach(authorities::add);
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @ManyToMany
    @JoinTable(
            name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "username"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    private Collection<Role> roles;

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }
}
