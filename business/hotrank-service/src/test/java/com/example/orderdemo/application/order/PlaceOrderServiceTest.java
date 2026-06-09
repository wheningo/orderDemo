package com.example.orderdemo.application.order;

import com.example.orderdemo.application.inventory.InventoryTccAction;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceOrderServiceTest {

    private OrderTccAction orderTccAction;
    private InventoryTccAction inventoryTccAction;
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        orderTccAction = mock(OrderTccAction.class);
        inventoryTccAction = mock(InventoryTccAction.class);
        service = new PlaceOrderService(orderTccAction, inventoryTccAction);
    }

    @Test
    void successfulPlaceOrder() {
        when(orderTccAction.tryCreate(any(), eq("Coffee"), eq(3))).thenReturn(true);
        when(inventoryTccAction.tryReserve(any(), eq("SKU-COFFEE"), eq(3))).thenReturn(true);

        PlaceOrderResult result = service.placeOrder("Coffee", 3, "SKU-COFFEE");
        assertTrue(result.success());
    }

    @Test
    void inventoryRejectThrowsOversellException() {
        when(orderTccAction.tryCreate(any(), eq("Coffee"), eq(3))).thenReturn(true);
        when(inventoryTccAction.tryReserve(any(), eq("SKU-COFFEE"), eq(3))).thenReturn(false);

        assertThrows(OversellRejectedException.class,
                () -> service.placeOrder("Coffee", 3, "SKU-COFFEE"));
    }

    @Test
    void orderTryFailureThrows() {
        when(orderTccAction.tryCreate(any(), eq("Coffee"), eq(3))).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> service.placeOrder("Coffee", 3, "SKU-COFFEE"));
    }
}