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
 * testable without an HTTP request, rather than only through an HTTP POST.
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrdersRepository ordersRepo;
    private final OrderItemsRepository orderItemsRepo;
    private final BeveragesRepository beveragesRepo;
    private final Validator validator;

    /** Thrown when a cart asks for more of a beverage than remains in stock. */
    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String beverageName, int requested, int available) {
            super(String.format("Only %d of %s left — you asked for %d.", available, beverageName, requested));
        }
    }

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
     * <p>Every line is locked and checked before anything is written, so a cart
     * that exceeds stock is rejected outright and the transaction rolls back
     * without a partial order.
     *
     * @throws InsufficientStockException if any line exceeds available stock
     * @throws OrderRejectedException if the order fails validation
     */
    @Transactional
    public Order placeOrder(User user, CartDTO cart) {
        // Lock and check every line first. Doing this up front means a rejection
        // leaves nothing behind, and holding the locks until commit stops a
        // concurrent checkout from selling the same units.
        for (var entry : cart.beverages.entrySet()) {
            var requested = entry.getValue();
            var locked = this.beveragesRepo
                    .findByIdForUpdate(entry.getKey().getId())
                    .orElseThrow(
                            () -> new InsufficientStockException(entry.getKey().getName(), requested, 0));

            if (locked.getInStock() < requested) {
                throw new InsufficientStockException(locked.getName(), requested, locked.getInStock());
            }
        }

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

        // No Math.max clamp: the checks above guarantee stock is sufficient, so
        // clamping could only ever hide a defect.
        var reducedPiecesBeverages = cart.beverages.entrySet().stream()
                .map(entry -> {
                    var beverage = entry.getKey();
                    beverage.setInStock(beverage.getInStock() - entry.getValue());
                    return beverage;
                })
                .toList();
        this.beveragesRepo.saveAll(reducedPiecesBeverages);

        return saved;
    }
}
