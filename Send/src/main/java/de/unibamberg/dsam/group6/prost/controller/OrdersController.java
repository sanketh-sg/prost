package de.unibamberg.dsam.group6.prost.controller;

import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.service.UserErrorManager;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import de.unibamberg.dsam.group6.prost.util.Toast;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class OrdersController {
    private final OrdersRepository ordersRepo;
    private final UserErrorManager errors;

    @GetMapping("/orders")
    public String showOrders(Principal principal, Model model) {
        var orders = this.ordersRepo.findAllByUser_username(principal.getName());
        model.addAttribute("orders", orders);
        return "pages/orders";
    }

    @GetMapping("/orders/{id}")
    public String showOrders(@PathVariable Long id, Principal principal, Model model) {
        var order = this.ordersRepo.findByUser_usernameAndId(principal.getName(), id);

        if (order.isEmpty()) {
            this.errors.addToast(Toast.error("This order does not exist."));
            return "redirect:/orders";
        }

        model.addAttribute("order", order.get());
        model.addAttribute("cartDTO", CartDTO.fromOrder(order.get()));
        return "pages/order_detail";
    }
}
