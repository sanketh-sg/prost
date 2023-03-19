package de.unibamberg.dsam.group6.prost.entity;

import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.validator.constraints.URL;

@Entity(name = "crates")
@NamedEntityGraph(
        name = "crate-with-bottle",
        attributeNodes = @NamedAttributeNode(value = "bottle", subgraph = "bottle.name"),
        subgraphs = @NamedSubgraph(name = "bottle.name", attributeNodes = @NamedAttributeNode("name")))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Crate extends Beverage {
    @Column(name = "name", nullable = false)
    @NotEmpty
    @Pattern(regexp = "\\w+", message = "Name must only contain letters or numbers.")
    private String name;

    @Column(name = "crate_pic")
    @URL
    private String cratePic;

    @Column(name = "no_of_bottles")
    @Min(1)
    private int noOfBottles;

    @Column(name = "price")
    @Min(1)
    private double price;

    @Column(name = "crates_in_stock")
    @Min(0)
    private int cratesInStock;

    // region Relations

    @ManyToOne
    @JoinColumn(name = "bottle_id")
    @NotNull(message = "Must choose a bottle.")
    private Bottle bottle;

    // endregion

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Crate crate = (Crate) o;
        return getId() != null && Objects.equals(getId(), crate.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String getPicture() {
        return this.cratePic;
    }

    @Override
    public int getInStock() {
        return this.cratesInStock;
    }

    @Override
    public void setInStock(int inStock) {
        this.cratesInStock = inStock;
    }
}
