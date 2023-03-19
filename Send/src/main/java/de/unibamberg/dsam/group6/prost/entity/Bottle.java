package de.unibamberg.dsam.group6.prost.entity;

import static java.lang.String.format;

import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.validator.constraints.URL;

@Entity(name = "bottles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bottle extends Beverage {
    @Column(name = "name")
    @NotEmpty
    @Pattern(regexp = "\\w+", message = "Name must only contain letters or numbers.")
    private String name;

    @Column(name = "bottle_pic")
    @URL
    private String bottlePic;

    @Column(name = "volume")
    @Min(0)
    private double volume;

    @Column(name = "volume_percent")
    @Setter(AccessLevel.NONE)
    @Min(0)
    private double volumePercent;

    @Column(name = "price")
    @Min(1)
    private double price;

    @Column(name = "supplier")
    @NotEmpty
    private String supplier;

    @Column(name = "in_stock")
    @Min(0)
    private int inStock;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Bottle bottle = (Bottle) o;
        return getId() != null && Objects.equals(getId(), bottle.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String getPicture() {
        return this.bottlePic;
    }

    @Override
    public int getInStock() {
        return this.inStock;
    }

    @Override
    public void setInStock(int inStock) {
        this.inStock = inStock;
    }

    @Override
    public double getPrice() {
        return this.price;
    }

    public boolean isAlcoholic() {
        return this.volumePercent > 0;
    }

    @Override
    public String toString() {
        return format("%.2f l - %s %s", this.volume, this.supplier, this.name);
    }
}
