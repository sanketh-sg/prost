package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.repository.BeveragesRepository;
import de.unibamberg.dsam.group6.prost.repository.UserRepository;
import de.unibamberg.dsam.group6.prost.service.Cart;
import de.unibamberg.dsam.group6.prost.service.OrderService;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.Toast;
import de.unibamberg.dsam.group6.prost.util.exception.BadRequestException;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class CartController {
    private final Cart cart;
    private final BeveragesRepository beveragesRepo;
    private final UserRepository userRepo;
    private final OrderService orderService;
    private final UserErrorManager errors;

    @GetMapping("/cart")
    public String showCart(Model model, Principal principal) {
        final var user = this.userRepo.findUserByUsername(principal.getName()).orElseThrow();
        model.addAttribute("cart", this.cart.getCartState());
        model.addAttribute("user", user);
        return "pages/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(
            @RequestParam Optional<Long> beverageId,
            @RequestParam Optional<Integer> count,
            @RequestParam Optional<String> next)
            throws BadRequestException {
        if (beverageId.isEmpty()) {
            throw new BadRequestException();
        }
        final var beverage = this.beveragesRepo.findById(beverageId.get());

        if (beverage.isPresent() && count.orElse(1) <= beverage.get().getInStock()) {
            this.cart.addToCart(beverageId.get(), count.orElse(1));
        }

        return "redirect:" + next.orElse("/");
    }

    @PostMapping("/cart/remove")
    public String removeFromCart(
            @RequestParam Optional<Long> beverageId,
            @RequestParam Optional<Boolean> all,
            @RequestParam Optional<String> next)
            throws BadRequestException {
        if (beverageId.isEmpty()) {
            throw new BadRequestException();
        }
        if (all.orElse(false)) {
            this.cart.removeAllFromCart(beverageId.get());
        } else {
            this.cart.removeOneFromCart(beverageId.get());
        }
        return "redirect:" + next.orElse("/");
    }

    @PostMapping("/cart/submit")
    public String submitCart(Principal principal) {
        var user = this.userRepo.findUserByUsername(principal.getName()).orElseThrow();
        var cartState = this.cart.getCartState();

        if (cartState.beverages.isEmpty()) {
            this.errors.addToast(Toast.notice("No items in cart."));
            return "redirect:/cart";
        }

        try {
            this.orderService.placeOrder(user, cartState);
        } catch (OrderService.OrderRejectedException e) {
            e.getMessages().forEach(message -> this.errors.addToast(Toast.error(message)));
            return "redirect:/cart";
        } catch (OrderService.InsufficientStockException e) {
            this.errors.addToast(Toast.error(e.getMessage()));
            return "redirect:/cart";
        }

        this.cart.clear();
        return "redirect:/order_success";
    }
}
