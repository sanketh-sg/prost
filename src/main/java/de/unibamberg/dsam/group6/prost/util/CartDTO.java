package de.unibamberg.dsam.group6.prost.util;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import de.unibamberg.dsam.group6.prost.entity.Order;
import de.unibamberg.dsam.group6.prost.entity.OrderItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CartDTO {
    public final Map<Beverage, Integer> beverages = new HashMap<>();

    /**
     * Constructs a CartDTO object from an Order instance.
     */
    public static CartDTO fromOrder(Order order) {
        var self = new CartDTO();
        self.totalPrice = order.getPrice();
        for (var orderItem : order.getOrderItems()) {
            self.beverages.merge(orderItem.getBeverage(), orderItem.getQuantity(), Integer::sum);
        }
        return self;
    }

    private BigDecimal totalPrice = null;

    public BigDecimal getTotalPrice() {
        if (this.totalPrice == null) {
            this.recalculatePrice();
        }
        return this.totalPrice;
    }

    /**
     * Calculate prices of all beverages in cart
     */
    public void recalculatePrice() {
        this.totalPrice = this.beverages.entrySet().stream()
                .map(e -> e.getKey().getPrice().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Maps CartDTO object with cart items as Beverage instances into
     * instances of OrderItem (they are not persisted yet).
     *
     * <p>One row per distinct beverage, carrying its quantity — not one row per
     * unit, which used to make a crate of 24 bottles write 24 identical rows.
     */
    public List<OrderItem> getOrderItems() {
        var orderItems = new ArrayList<OrderItem>();
        var position = 0;

        for (var b : this.beverages.entrySet()) {
            var beverage = b.getKey();
            var oi = new OrderItem();
            oi.setPrice(beverage.getPrice());
            oi.setQuantity(b.getValue());
            oi.setBeverage(beverage);
            oi.setPosition(String.valueOf(position));
            orderItems.add(oi);
            position++;
        }
        return orderItems;
    }
}
