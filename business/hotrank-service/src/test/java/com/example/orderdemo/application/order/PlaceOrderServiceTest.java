package com.example.orderdemo.application.order;

import com.example.contracts.result.CommandResult;
import com.example.orderdemo.application.inventory.InventoryTccService;
import com.example.orderdemo.domain.order.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaceOrderServiceTest {

    private OrderTccService orderTccService;
    private InventoryTccService inventoryTccService;
    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        orderTccService = mock(OrderTccService.class);
        inventoryTccService = mock(InventoryTccService.class);
        service = new PlaceOrderService(orderTccService, inventoryTccService);
    }

    @Test
    void successfulPlaceOrder() {
        when(orderTccService.tryCreate(anyString(), eq("Coffee"), eq(3))).thenReturn(new OrderId(1L));
        when(inventoryTccService.tryReserve(anyString(), eq("SKU-COFFEE"), eq(3))).thenReturn(CommandResult.ok());

        PlaceOrderResult result = service.placeOrder("Coffee", 3, "SKU-COFFEE");
        assertTrue(result.success());
        assertNotNull(result.orderId());

        verify(orderTccService).confirm(new OrderId(1L));
        verify(inventoryTccService).confirm(anyString());
    }

    @Test
    void inventoryRejectCausesCancel() {
        when(orderTccService.tryCreate(anyString(), eq("Coffee"), eq(3))).thenReturn(new OrderId(1L));
        when(inventoryTccService.tryReserve(anyString(), eq("SKU-COFFEE"), eq(3)))
                .thenReturn(CommandResult.oversellRejected("SKU-COFFEE", 3, 0));

        PlaceOrderResult result = service.placeOrder("Coffee", 3, "SKU-COFFEE");
        assertFalse(result.success());
        assertTrue(result.reason().contains("Oversell"));

        verify(inventoryTccService).cancel(anyString());
        verify(orderTccService).cancel(new OrderId(1L));
        verify(orderTccService, never()).confirm(any());
    }

    @Test
    void orderTryExceptionCausesCancel() {
        when(orderTccService.tryCreate(anyString(), eq("Coffee"), eq(3)))
                .thenThrow(new RuntimeException("DB error"));

        PlaceOrderResult result = service.placeOrder("Coffee", 3, "SKU-COFFEE");
        assertFalse(result.success());

        verify(inventoryTccService).cancel(anyString());
    }

    @Test
    void confirmFailureCausesCancel() {
        when(orderTccService.tryCreate(anyString(), eq("Coffee"), eq(3))).thenReturn(new OrderId(1L));
        when(inventoryTccService.tryReserve(anyString(), eq("SKU-COFFEE"), eq(3))).thenReturn(CommandResult.ok());
        doThrow(new RuntimeException("confirm failed")).when(orderTccService).confirm(any());

        PlaceOrderResult result = service.placeOrder("Coffee", 3, "SKU-COFFEE");
        assertFalse(result.success());

        verify(inventoryTccService).cancel(anyString());
        verify(orderTccService).cancel(new OrderId(1L));
    }
}