package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CartDTOTest {
    static final double PRICE1 = 256d;
    static final double PRICE2 = 512d;

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
        assertThat(this.getDTO().getTotalPrice()).isEqualTo(PRICE1 + PRICE2);
    }

    @Test
    void multipleItems() {
        final var price = 1234d;
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
        assertThat(this.getDTO().getTotalPrice()).isEqualTo(PRICE1 + PRICE2 + count * price);
    }

    @Test
    void translateToCartItems() {
        final var price = 1234d;
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
                        .reduce(0.0, (state, oi) -> state + oi.getPrice(), Double::sum))
                .isEqualTo(this.cart.getCartState().getTotalPrice());
    }
}
