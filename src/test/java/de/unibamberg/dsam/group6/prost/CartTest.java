package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.*;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.support.TestData;
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

        this.firstId = TestData.saveBottle(this.bottles, "TestBottleA", 100).getId();
        this.secondId = TestData.saveBottle(this.bottles, "TestBottleB", 100).getId();
        this.absentId = this.secondId + 1000L;

        this.cart.addToCart(this.firstId, 1);
        this.cart.addToCart(this.secondId, 3);
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
