package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.repository.BeveragesRepository;
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
 * Characterizes checkout.
 *
 * <p>Orders persist across methods within this class (no rollback), so order
 * assertions are expressed as deltas rather than absolute counts.
 */
@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class CheckoutTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    @Autowired
    BeveragesRepository beverages;

    @Autowired
    OrdersRepository orders;

    MockHttpSession session;

    @BeforeEach
    void setUp() {
        this.session = new MockHttpSession();
    }

    private int orderCount() {
        return this.orders.findAllByUser_username("admin").size();
    }

    private int stockOf(Long id) {
        return this.beverages.findById(id).orElseThrow().getInStock();
    }

    private void add(Long id, int count) throws Exception {
        this.mvc
                .perform(post("/cart/add")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", id.toString())
                        .param("count", Integer.toString(count)))
                .andExpect(status().is3xxRedirection());
    }

    private void submit(String expectedRedirect) throws Exception {
        this.mvc
                .perform(post("/cart/submit").session(this.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect));
    }

    @Test
    void submittingAnEmptyCartRedirectsBackToCart() throws Exception {
        var before = this.orderCount();

        this.submit("/cart");

        assertThat(this.orderCount()).isEqualTo(before);
    }

    @Test
    void submittingAPopulatedCartCreatesAnOrder() throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, "CheckoutBottle", 100).getId();
        var before = this.orderCount();
        this.add(bottleId, 2);

        this.submit("/order_success");

        assertThat(this.orderCount()).isEqualTo(before + 1);
    }

    @Test
    void checkoutDecrementsStock() throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, "StockBottle", 100).getId();
        this.add(bottleId, 2);

        this.submit("/order_success");

        assertThat(this.stockOf(bottleId)).isEqualTo(98);
    }

    @Test
    void checkoutPersistsOrderItems() throws Exception {
        var bottleId = TestData.saveBottle(this.bottles, "ItemsBottle", 100).getId();
        this.add(bottleId, 3);

        this.submit("/order_success");

        var placed = this.orders.findAllByUser_username("admin");
        var latest = placed.get(placed.size() - 1);
        assertThat(latest.getOrderItems()).hasSize(1);
        assertThat(latest.getOrderItems().get(0).getQuantity()).isEqualTo(3);
    }

    /**
     * add-to-cart validates stock per request, so two individually valid requests
     * can build a cart that exceeds it. Checkout must catch that.
     */
    @Test
    void checkoutIsRejectedWhenTheCartExceedsStock() throws Exception {
        var scarce = TestData.saveBottle(this.bottles, "ScarceCheckout", 2).getId();
        var before = this.orderCount();

        this.add(scarce, 2);
        this.add(scarce, 2); // cart now holds 4, stock is 2

        this.submit("/cart");

        assertThat(this.orderCount()).isEqualTo(before);
    }

    @Test
    void rejectedCheckoutLeavesStockUntouched() throws Exception {
        var scarce = TestData.saveBottle(this.bottles, "UntouchedStock", 2).getId();

        this.add(scarce, 2);
        this.add(scarce, 2);

        this.submit("/cart");

        assertThat(this.stockOf(scarce)).isEqualTo(2);
    }

    /** The boundary: asking for exactly the remaining stock must still succeed. */
    @Test
    void checkoutSucceedsWhenStockIsExactlySufficient() throws Exception {
        var scarce = TestData.saveBottle(this.bottles, "ExactStock", 4).getId();
        var before = this.orderCount();

        this.add(scarce, 4);

        this.submit("/order_success");

        assertThat(this.orderCount()).isEqualTo(before + 1);
        assertThat(this.stockOf(scarce)).isZero();
    }
}
