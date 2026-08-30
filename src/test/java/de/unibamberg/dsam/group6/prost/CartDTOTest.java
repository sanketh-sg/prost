package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
public class CartDTOTest {
    static final BigDecimal PRICE1 = BigDecimal.valueOf(256d);
    static final BigDecimal PRICE2 = BigDecimal.valueOf(512d);

    @Autowired
    Cart cart;

    @Autowired
    BottlesRepository bottlesRepository;

    @BeforeEach
    void addTestToCart() {
        var a = this.bottlesRepository.save(Bottle.builder()
                .name("bottleA")
                .price(PRICE1)
                .inStock(3)
                .supplier("supplierA")
                .volume(0.5)
                .volumePercent(6.5)
                .build());
        var b = this.bottlesRepository.save(Bottle.builder()
                .name("bottleB")
                .price(PRICE2)
                .inStock(6)
                .supplier("supplierB")
                .volume(0.33)
                .volumePercent(4.3)
                .build());
        this.cart.addToCart(a.getId(), 1);
        this.cart.addToCart(b.getId(), 1);
    }

    @Test
    void contextLoads() {
        assertThat(this.cart).isNotNull();
    }

    CartDTO getDTO() {
        return this.cart.getCartState();
    }

    @Test
    void totalPrice() {
        assertThat(this.getDTO().getTotalPrice()).isEqualByComparingTo(PRICE1.add(PRICE2));
    }

    @Test
    void multipleItems() {
        final var price = BigDecimal.valueOf(1234d);
        final var count = 9;

        final var b = this.bottlesRepository.save(Bottle.builder()
                .name("bottleC")
                .price(price)
                .inStock(9)
                .supplier("supplierC")
                .volume(0.2)
                .volumePercent(7.3)
                .build());
        this.cart.addToCart(b.getId(), count);
        assertThat(this.getDTO().getTotalPrice())
                .isEqualByComparingTo(PRICE1.add(PRICE2).add(price.multiply(BigDecimal.valueOf(count))));
    }

    @Test
    void translateToCartItems() {
        final var price = BigDecimal.valueOf(1234d);
        final var count = 9;

        final var b = this.bottlesRepository.save(Bottle.builder()
                .name("bottleC")
                .price(price)
                .inStock(9)
                .supplier("supplierC")
                .volume(0.2)
                .volumePercent(7.3)
                .build());
        this.cart.addToCart(b.getId(), count);
        assertThat(this.getDTO().getOrderItems().stream()
                        .map(oi -> oi.getPrice().multiply(BigDecimal.valueOf(oi.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(this.cart.getCartState().getTotalPrice());
    }
}
