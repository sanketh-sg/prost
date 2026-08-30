package de.unibamberg.dsam.group6.prost.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.util.*;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;

@Entity(name = "orders")
@NamedEntityGraph(
        name = "order-beverages",
        attributeNodes = {
            @NamedAttributeNode(value = "orderItems", subgraph = "orderItem.beverage"),
            @NamedAttributeNode(value = "user", subgraph = "user.username")
        },
        subgraphs = {
            @NamedSubgraph(
                    name = "orderItem.beverage",
                    attributeNodes = {@NamedAttributeNode("beverage")}),
            @NamedSubgraph(name = "user.username", attributeNodes = @NamedAttributeNode("username"))
        })
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "price")
    @Min(1)
    private double price;

    @Column(name = "created_on", updatable = false, nullable = false)
    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdOn;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> orderItems;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Order order = (Order) o;
        return id != null && Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
