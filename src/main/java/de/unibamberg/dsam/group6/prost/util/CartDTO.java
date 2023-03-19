package de.unibamberg.dsam.group6.prost.util;

import de.unibamberg.dsam.group6.prost.entity.Beverage;
import de.unibamberg.dsam.group6.prost.entity.Order;
import de.unibamberg.dsam.group6.prost.entity.OrderItem;
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
            var currentCount = self.beverages.getOrDefault(orderItem.getBeverage(), 0);
            self.beverages.put(orderItem.getBeverage(), currentCount + 1);
        }
        return self;
    }

    private double totalPrice = -1;

    public double getTotalPrice() {
        if (this.totalPrice == -1) {
            this.recalculatePrice();
        }
        return this.totalPrice;
    }

    /**
     * Calculate prices of all beverages in cart
     */
    public void recalculatePrice() {
        this.totalPrice = this.beverages.keySet().stream()
                .reduce(0.0, (prev, cur) -> prev + this.beverages.get(cur) * cur.getPrice(), Double::sum);
    }

    /**
     * Maps CartDTO object with cart items as Beverage instances into
     * instances of OrderItem (they are not persisted yet).
     */
    public List<OrderItem> getOrderItems() {
        var orderItems = new ArrayList<OrderItem>();
        var position = 0;

        for (var b : this.beverages.entrySet()) {
            for (int i = 0; i < b.getValue(); i++) {
                var beverage = b.getKey();
                var oi = new OrderItem();
                oi.setPrice(beverage.getPrice());
                oi.setBeverage(beverage);
                oi.setPosition(String.valueOf(position));
                orderItems.add(oi);
                position++;
            }
        }
        return orderItems;
    }
}
