package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.*;

import de.unibamberg.dsam.group6.prost.service.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class CartTest {
    @Autowired
    Cart cart;

    @BeforeEach
    void addTestToCart() {
        this.cart.addToCart(1L, 1);
        this.cart.addToCart(2L, 3);
    }

    @Test
    void contextLoads() {
        assertThat(this.cart).isNotNull();
    }

    @Test
    void addToCart() {
        assertThat(this.cart.getCartItemIds().containsKey(1L)).isTrue();
        assertThat(this.cart.getCartItemIds().containsKey(3L)).isFalse();
    }

    @Test
    void removeOneFromCart() {
        this.cart.removeOneFromCart(1L);
        this.cart.removeOneFromCart(2L);

        var cartItems = this.cart.getCartItemIds();
        assertThat(cartItems.containsKey(1L)).isFalse();
        assertThat(cartItems.get(2L)).isEqualTo(2);
    }

    @Test
    void removeAllFromCart() {
        this.cart.removeAllFromCart(1L);
        this.cart.removeAllFromCart(2L);

        var cartItems = this.cart.getCartItemIds();
        assertThat(cartItems.containsKey(1L)).isFalse();
        assertThat(cartItems.containsKey(2L)).isFalse();
    }
}
