package de.unibamberg.dsam.group6.prost.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
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

    @ManyToOne
    @JoinColumn(name = "bottle_id")
    @NotNull(message = "Must choose a bottle.")
    private Bottle bottle;

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
