package de.unibamberg.dsam.group6.prost.entity;

import static java.lang.String.format;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.*;
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

    @Column(name = "price", precision = 10, scale = 2)
    @DecimalMin("0.01")
    private BigDecimal price;

    @Column(name = "supplier")
    @NotEmpty
    private String supplier;

    @Column(name = "in_stock")
    @Min(0)
    private int inStock;

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
    public BigDecimal getPrice() {
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
