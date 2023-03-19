package de.unibamberg.dsam.group6.prost.entity;

import static java.lang.String.format;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;
import org.hibernate.validator.constraints.Length;

@Entity(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address implements Serializable {
    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "street", nullable = false)
    @NotEmpty
    private String street;

    @Column(name = "number", nullable = false)
    @NotEmpty
    private String number;

    @Column(name = "postal_code", nullable = false, length = 5)
    @NotEmpty
    @Length(max = 5)
    private String postalCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Address address = (Address) o;
        return id != null && Objects.equals(id, address.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return format("%s\n%s %s", this.postalCode, this.street, this.number);
    }
}
