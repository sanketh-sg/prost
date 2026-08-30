package de.unibamberg.dsam.group6.prost.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;
import lombok.*;
import org.hibernate.Hibernate;

@Entity(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.NONE)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "position")
    @Pattern(regexp = "\\d+")
    private String position;

    @Column(name = "price")
    @Min(1)
    private double price;

    @ManyToOne(targetEntity = Order.class)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "beverage_id")
    @NotNull
    private Beverage beverage;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        OrderItem orderItem = (OrderItem) o;
        return id != null && Objects.equals(id, orderItem.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
