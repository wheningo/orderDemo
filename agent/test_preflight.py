"""Tests for agent pre-flight risk check."""

import time
from preflight import (
    preflight_check_boost,
    preflight_check_inventory,
    preflight_check_schedule,
    on_dispatch_result,
    reset_state,
    MAX_WEIGHT,
    MAX_QTY,
    MAX_COMMANDS_PER_MINUTE,
    MAX_CONSECUTIVE_REJECTIONS,
)


class TestPreflightBoost:

    def setup_method(self):
        reset_state()

    def test_normal_weight_passes(self):
        result = preflight_check_boost(50, "CN")
        assert result.passed

    def test_weight_exceeds_soft_limit(self):
        result = preflight_check_boost(MAX_WEIGHT + 1, "CN")
        assert not result.passed
        assert "soft limit" in result.reason

    def test_weight_at_soft_limit_passes(self):
        result = preflight_check_boost(MAX_WEIGHT, "CN")
        assert result.passed


class TestPreflightInventory:

    def setup_method(self):
        reset_state()

    def test_normal_qty_passes(self):
        result = preflight_check_inventory(100, "SKU-1")
        assert result.passed

    def test_qty_exceeds_soft_limit(self):
        result = preflight_check_inventory(MAX_QTY + 1, "SKU-1")
        assert not result.passed
        assert "soft limit" in result.reason


class TestPreflightSchedule:

    def setup_method(self):
        reset_state()

    def test_valid_delay_passes(self):
        result = preflight_check_schedule(5)
        assert result.passed

    def test_zero_delay_blocked(self):
        result = preflight_check_schedule(0)
        assert not result.passed

    def test_excessive_delay_blocked(self):
        result = preflight_check_schedule(1500)
        assert not result.passed
        assert "24h" in result.reason


class TestCircuitBreaker:

    def setup_method(self):
        reset_state()

    def test_opens_after_consecutive_rejections(self):
        for i in range(MAX_CONSECUTIVE_REJECTIONS):
            on_dispatch_result(False, "rejected")

        result = preflight_check_boost(10, "CN")
        assert not result.passed
        assert "Circuit breaker" in result.reason

    def test_resets_on_success(self):
        for i in range(MAX_CONSECUTIVE_REJECTIONS - 1):
            on_dispatch_result(False, "rejected")
        on_dispatch_result(True)

        result = preflight_check_boost(10, "CN")
        assert result.passed


class TestFrequencyThrottle:

    def setup_method(self):
        reset_state()

    def test_throttles_after_limit(self):
        for i in range(MAX_COMMANDS_PER_MINUTE):
            on_dispatch_result(True)

        result = preflight_check_boost(10, "CN")
        assert not result.passed
        assert "Self-throttle" in result.reason