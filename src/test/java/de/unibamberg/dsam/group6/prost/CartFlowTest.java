package de.unibamberg.dsam.group6.prost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import de.unibamberg.dsam.group6.prost.repository.BottlesRepository;
import de.unibamberg.dsam.group6.prost.support.IntegrationTest;
import de.unibamberg.dsam.group6.prost.support.TestData;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Characterizes add/remove through the real HTTP path.
 *
 * <p>The Cart bean is session-backed, so every request in a flow shares one
 * MockHttpSession and cart contents are read back from the /cart model rather
 * than from the injected bean, which would resolve a different session.
 */
@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class CartFlowTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    BottlesRepository bottles;

    MockHttpSession session;
    Long bottleId;

    @BeforeEach
    void setUp() {
        this.session = new MockHttpSession();
        this.bottleId = TestData.saveBottle(this.bottles, "CartBottle", 100).getId();
    }

    private Map<Beverage, Integer> cartContents() throws Exception {
        var result = this.mvc
                .perform(get("/cart").session(this.session))
                .andExpect(status().isOk())
                .andReturn();
        var cart = (CartDTO) result.getModelAndView().getModel().get("cart");
        return cart.beverages;
    }

    private void add(long id, int count) throws Exception {
        this.mvc
                .perform(post("/cart/add")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", Long.toString(id))
                        .param("count", Integer.toString(count)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void addingABeveragePutsItInTheCart() throws Exception {
        this.add(this.bottleId, 3);

        assertThat(this.cartContents().values()).containsExactly(3);
    }

    @Test
    void addingTwiceAccumulates() throws Exception {
        this.add(this.bottleId, 2);
        this.add(this.bottleId, 3);

        assertThat(this.cartContents().values()).containsExactly(5);
    }

    @Test
    void removingOneDecrementsTheCount() throws Exception {
        this.add(this.bottleId, 3);

        this.mvc
                .perform(post("/cart/remove")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", this.bottleId.toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(this.cartContents().values()).containsExactly(2);
    }

    @Test
    void removingAllClearsThatBeverage() throws Exception {
        this.add(this.bottleId, 3);

        this.mvc
                .perform(post("/cart/remove")
                        .session(this.session)
                        .with(csrf())
                        .param("beverageId", this.bottleId.toString())
                        .param("all", "true"))
                .andExpect(status().is3xxRedirection());

        assertThat(this.cartContents()).isEmpty();
    }

    @Test
    void addingAnUnknownBeverageLeavesTheCartEmpty() throws Exception {
        this.add(this.bottleId + 100_000L, 1);

        assertThat(this.cartContents()).isEmpty();
    }

    /**
     * CHARACTERIZATION — addToCart rejects a single request exceeding stock, but
     * only per request. See CheckoutTest for the consequence.
     */
    @Test
    void addingMoreThanStockInOneRequestIsIgnored() throws Exception {
        var scarce = TestData.saveBottle(this.bottles, "ScarceBottle", 2).getId();

        this.add(scarce, 5);

        assertThat(this.cartContents()).isEmpty();
    }
}
