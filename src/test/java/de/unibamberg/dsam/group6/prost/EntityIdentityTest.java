package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;

import de.unibamberg.dsam.group6.prost.entity.Bottle;
import de.unibamberg.dsam.group6.prost.entity.Crate;
import de.unibamberg.dsam.group6.prost.entity.User;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

/**
 * Entity identity is the database id.
 *
 * <p>{@code CartDTO} keys a {@code HashMap} by Beverage. The subclasses previously returned
 * {@code getClass().hashCode()} — a constant — so every beverage collided into a single bucket
 * and the map degraded to a linked list. It was correct only by accident, because one request
 * loads each beverage exactly once.
 */
class EntityIdentityTest {
    @Test
    void beveragesWithDifferentIdsDoNotShareOneHashBucket() {
        var a = Bottle.builder().name("a").build();
        var b = Bottle.builder().name("b").build();
        a.setId(1L);
        b.setId(2L);

        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }

    @Test
    void twoLoadsOfTheSameBeverageAreOneMapKey() {
        var first = Bottle.builder().name("same").build();
        var second = Bottle.builder().name("same").build();
        first.setId(7L);
        second.setId(7L);

        var map = new HashMap<Bottle, Integer>();
        map.put(first, 1);
        map.put(second, 2);

        assertThat(map).hasSize(1).containsValue(2);
    }

    @Test
    void cratesAndBottlesSharingAnIdAreNotEqual() {
        var bottle = Bottle.builder().name("thing").build();
        var crate = Crate.builder().name("thing").build();
        bottle.setId(3L);
        crate.setId(3L);

        assertThat(bottle).isNotEqualTo(crate);
    }

    /** Two distinct unsaved beverages are not the same product, so they must not collapse. */
    @Test
    void unsavedBeveragesAreDistinct() {
        var a = Bottle.builder().name("unsaved").build();
        var b = Bottle.builder().name("unsaved").build();

        var map = new HashMap<Bottle, Integer>();
        map.put(a, 1);
        map.put(b, 2);

        assertThat(map).hasSize(2);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void usersWithDifferentUsernamesDoNotShareOneHashBucket() {
        var a = User.builder().username("alice").build();
        var b = User.builder().username("bob").build();

        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }

    /** equals compares username, so hashCode must agree or map lookups break. */
    @Test
    void usersWithTheSameUsernameAgreeOnEqualsAndHashCode() {
        var a = User.builder().username("alice").build();
        var b = User.builder().username("alice").build();

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
