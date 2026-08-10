package de.unibamberg.dsam.group6.prost.service;

import de.unibamberg.dsam.group6.prost.entity.Order;
import de.unibamberg.dsam.group6.prost.entity.User;
import de.unibamberg.dsam.group6.prost.repository.BeveragesRepository;
import de.unibamberg.dsam.group6.prost.repository.OrderItemsRepository;
import de.unibamberg.dsam.group6.prost.repository.OrdersRepository;
import de.unibamberg.dsam.group6.prost.util.CartDTO;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places orders.
 *
 * <p>Extracted from CartController so that order creation is reachable and
 * testable without an HTTP request. This is a pure move: the logic is identical
 * to what the controller did, including the stock clamp described below.
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrdersRepository ordersRepo;
    private final OrderItemsRepository orderItemsRepo;
    private final BeveragesRepository beveragesRepo;
    private final Validator validator;

    /** Thrown when an order fails bean validation. Carries the messages for display. */
    public static class OrderRejectedException extends RuntimeException {
        private final Set<String> messages;

        public OrderRejectedException(Set<String> messages) {
            super(String.join("; ", messages));
            this.messages = messages;
        }

        public Set<String> getMessages() {
            return this.messages;
        }
    }

    /**
     * Persists an order for the given cart contents and decrements stock.
     *
     * <p><strong>Known defect, carried over deliberately.</strong> Stock is not
     * re-checked here, and {@code Math.max(reduced, 0)} clamps the result to zero
     * rather than rejecting an oversized order. Add-to-cart validates stock per
     * request, so two individually valid requests can build a cart exceeding it.
     * Preserved unchanged so this extraction is behaviour-neutral and the fix
     * lands as its own reviewable diff.
     *
     * @throws OrderRejectedException if the order fails validation
     */
    @Transactional
    public Order placeOrder(User user, CartDTO cart) {
        var order = new Order();
        order.setUser(user);
        order.setPrice(cart.getTotalPrice());

        var violations = this.validator.validate(order);
        if (!violations.isEmpty()) {
            throw new OrderRejectedException(
                    violations.stream().map(v -> v.getMessage()).collect(Collectors.toSet()));
        }

        var saved = this.ordersRepo.save(order);

        var orderItems = cart.getOrderItems();
        for (var item : orderItems) {
            item.setOrder(saved);
        }
        this.orderItemsRepo.saveAll(orderItems);

        var reducedPiecesBeverages = cart.beverages.entrySet().stream()
                .map(entry -> {
                    var beverage = entry.getKey();
                    var reduced = beverage.getInStock() - entry.getValue();

                    beverage.setInStock(Math.max(reduced, 0));
                    return beverage;
                })
                .toList();
        this.beveragesRepo.saveAll(reducedPiecesBeverages);

        return saved;
    }
}
