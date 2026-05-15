package org.flossware.jremote;

public class OrderServiceImpl implements OrderService {
    private int orderCount = 0;

    @Override
    public String createOrder(int orderId) {
        orderCount++;
        return "Order created: " + orderId;
    }

    @Override
    public int getOrderCount() {
        return orderCount;
    }
}
