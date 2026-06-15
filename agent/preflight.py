"""Agent pre-flight risk check — first line of defense before commands hit the gateway.

This is the agent-side self-check layer (Level 1 of 3-level risk control):
  Level 1: Agent self-check (here) — local, fast, catches obvious issues
  Level 2: Go gateway RiskInterceptor → risk-control service (Drools)
  Level 3: Domain invariants (Java aggregates)

Checks:
  - Amount/weight bounds (soft limits, tighter than domain hard limits)
  - Frequency throttle (local counter, catches agent loops)
  - Consecutive rejection circuit breaker (stop hammering after N rejections)
"""

import time
from dataclasses import dataclass, field

MAX_WEIGHT = 80  # Agent soft limit (domain hard limit is 100)
MAX_QTY = 200  # Agent soft limit (domain rejects at available)
MAX_COMMANDS_PER_MINUTE = 15  # Agent self-throttle (gateway hard limit is higher)
MAX_CONSECUTIVE_REJECTIONS = 5  # Circuit breaker: stop after N rejections


@dataclass
class PreflightState:
    """Tracks agent command history for self-check decisions."""
    command_timestamps: list[float] = field(default_factory=list)
    consecutive_rejections: int = 0
    last_rejection_reason: str = ""

    def record_command(self):
        self.command_timestamps.append(time.time())
        # Keep only last 2 minutes
        cutoff = time.time() - 120
        self.command_timestamps = [t for t in self.command_timestamps if t > cutoff]

    def record_success(self):
        self.consecutive_rejections = 0
        self.last_rejection_reason = ""

    def record_rejection(self, reason: str):
        self.consecutive_rejections += 1
        self.last_rejection_reason = reason

    def commands_last_minute(self) -> int:
        cutoff = time.time() - 60
        return sum(1 for t in self.command_timestamps if t > cutoff)


# Global state per agent session
_state = PreflightState()


def reset_state():
    """Reset for testing."""
    global _state
    _state = PreflightState()


@dataclass
class PreflightResult:
    passed: bool
    reason: str = ""

    @staticmethod
    def ok():
        return PreflightResult(passed=True)

    @staticmethod
    def blocked(reason: str):
        return PreflightResult(passed=False, reason=reason)


def preflight_check_boost(weight: int, region: str) -> PreflightResult:
    """Pre-flight check for boost_exposure commands."""
    # Circuit breaker
    if _state.consecutive_rejections >= MAX_CONSECUTIVE_REJECTIONS:
        return PreflightResult.blocked(
            f"Circuit breaker open: {_state.consecutive_rejections} consecutive rejections "
            f"(last: {_state.last_rejection_reason})")

    # Frequency
    if _state.commands_last_minute() >= MAX_COMMANDS_PER_MINUTE:
        return PreflightResult.blocked(
            f"Self-throttle: {_state.commands_last_minute()} commands/min (limit: {MAX_COMMANDS_PER_MINUTE})")

    # Weight soft limit
    if weight > MAX_WEIGHT:
        return PreflightResult.blocked(
            f"Weight {weight} exceeds agent soft limit {MAX_WEIGHT} (domain limit: 100)")

    return PreflightResult.ok()


def preflight_check_inventory(qty: int, sku: str) -> PreflightResult:
    """Pre-flight check for allocate_promo_stock commands."""
    # Circuit breaker
    if _state.consecutive_rejections >= MAX_CONSECUTIVE_REJECTIONS:
        return PreflightResult.blocked(
            f"Circuit breaker open: {_state.consecutive_rejections} consecutive rejections")

    # Frequency
    if _state.commands_last_minute() >= MAX_COMMANDS_PER_MINUTE:
        return PreflightResult.blocked(
            f"Self-throttle: {_state.commands_last_minute()} commands/min (limit: {MAX_COMMANDS_PER_MINUTE})")

    # Qty soft limit
    if qty > MAX_QTY:
        return PreflightResult.blocked(
            f"Qty {qty} exceeds agent soft limit {MAX_QTY}")

    return PreflightResult.ok()


def preflight_check_schedule(delay_minutes: int) -> PreflightResult:
    """Pre-flight check for schedule commands."""
    if delay_minutes <= 0:
        return PreflightResult.blocked("Delay must be positive")
    if delay_minutes > 1440:
        return PreflightResult.blocked(f"Delay {delay_minutes}min exceeds 24h max")
    return PreflightResult.ok()


def on_dispatch_result(accepted: bool, reason: str = ""):
    """Call after dispatch to update self-check state."""
    _state.record_command()
    if accepted:
        _state.record_success()
    else:
        _state.record_rejection(reason)