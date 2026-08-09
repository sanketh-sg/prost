package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.*;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CartTest {
    @Autowired
    Cart cart;

    @Autowired
    BottlesRepository bottles;

    private Long firstId;
    private Long secondId;
    private Long absentId;

    /**
     * Cart.addToCart silently ignores unknown beverage ids, so the cart must be
     * seeded with real rows before anything can be added. The Cart bean is
     * session-backed and shared across test methods, so it is cleared here too.
     */
    @BeforeEach
    void addTestToCart() {
        this.cart.clear();

        // Bottle.name is constrained to \w+ — no spaces allowed.
        this.firstId = this.bottles.save(newBottle("TestBottleA")).getId();
        this.secondId = this.bottles.save(newBottle("TestBottleB")).getId();
        this.absentId = this.secondId + 1000L;

        this.cart.addToCart(this.firstId, 1);
        this.cart.addToCart(this.secondId, 3);
    }

    private static Bottle newBottle(String name) {
        return Bottle.builder()
                .name(name)
                .bottlePic("https://example.invalid/bottle.png")
                .volume(0.5)
                .volumePercent(5.0)
                .price(2.0)
                .supplier("Test Supplier")
                .inStock(100)
                .build();
    }

    @Test
    void contextLoads() {
        assertThat(this.cart).isNotNull();
    }

    @Test
    void addToCart() {
        assertThat(this.cart.getCartItemIds().containsKey(this.firstId)).isTrue();
        assertThat(this.cart.getCartItemIds().containsKey(this.absentId)).isFalse();
    }

    @Test
    void removeOneFromCart() {
        this.cart.removeOneFromCart(this.firstId);
        this.cart.removeOneFromCart(this.secondId);

        var cartItems = this.cart.getCartItemIds();
        assertThat(cartItems.containsKey(this.firstId)).isFalse();
        assertThat(cartItems.get(this.secondId)).isEqualTo(2);
    }

    @Test
    void removeAllFromCart() {
        this.cart.removeAllFromCart(this.firstId);
        this.cart.removeAllFromCart(this.secondId);

        var cartItems = this.cart.getCartItemIds();
        assertThat(cartItems.containsKey(this.firstId)).isFalse();
        assertThat(cartItems.containsKey(this.secondId)).isFalse();
    }
}
