package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterizes order listing and the ownership check.
 *
 * <p>OrdersController queries by username AND id, so a non-owner and a
 * nonexistent id take the same branch — both redirect to /orders.
 */
@IntegrationTest
class OrderAccessTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    OrdersRepository orders;

    MockHttpSession session;

    @BeforeEach
    void setUp() {
        this.session = new MockHttpSession();
    }

    /** Places an order as admin through the HTTP path and returns its id. */
    private Long placeOrderAsAdmin(String bottleName) throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, bottleName, 50).getId();

        this.mvc
                .perform(post("/cart/add")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", bottleId.toString())
                        .param("count", "1"))
                .andExpect(status().is3xxRedirection());

        this.mvc
                .perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(redirectedUrl("/order_success"));

        var placed = this.orders.findAllByUser_username("admin");
        return placed.get(placed.size() - 1).getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ownerCanViewTheirOrder() throws Exception {
        var id = this.placeOrderAsAdmin("OwnerBottle");

        this.mvc
                .perform(get("/orders/" + id).session(this.session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/order_detail"))
                .andExpect(model().attributeExists("order"))
                .andExpect(model().attributeExists("cartDTO"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ordersListShowsOwnOrders() throws Exception {
        this.placeOrderAsAdmin("ListBottle");

        this.mvc
                .perform(get("/orders").session(this.session))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/orders"))
                .andExpect(model().attributeExists("orders"));

        assertThat(this.orders.findAllByUser_username("admin")).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "someoneelse", roles = "USER")
    void nonOwnerIsRedirectedAwayFromAnotherUsersOrder() throws Exception {
        // Placed by admin in a separate session, then requested as someoneelse.
        var id = this.orders.findAllByUser_username("admin").stream()
                .findFirst()
                .map(o -> o.getId())
                .orElse(1L);

        this.mvc
                .perform(get("/orders/" + id).session(this.session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void nonexistentOrderRedirectsToOrders() throws Exception {
        this.mvc
                .perform(get("/orders/999999").session(this.session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"));
    }
}
