package de.unibamberg.dsam.group6.prost.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.Objects;
import lombok.*;
import org.hibernate.Hibernate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Beverage {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToMany(mappedBy = "beverage")
    private List<OrderItem> orderItem;

    public boolean isBottleInstance() {
        return this instanceof Bottle;
    }

    public boolean isCrateInstance() {
        return this instanceof Crate;
    }

    public abstract String getName();

    public abstract double getPrice();

    public abstract String getPicture();

    public abstract int getInStock();

    public abstract void setInStock(int inStock);

    /**
     * Identity is the database id.
     *
     * <p>Defined once here rather than per subclass: {@code CartDTO} keys a HashMap by Beverage,
     * and Bottle and Crate both returned {@code getClass().hashCode()} — a constant, so every
     * beverage collided into a single bucket.
     *
     * <p>Unsaved entities (id null) fall back to instance identity, which is correct: two
     * distinct unsaved beverages are not the same product.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Beverage other = (Beverage) o;
        return this.id != null && Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return this.id == null ? System.identityHashCode(this) : this.id.hashCode();
    }
}
