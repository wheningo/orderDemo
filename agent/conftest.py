"""Shared fixtures for agent tests."""

import pytest
from preflight import reset_state


@pytest.fixture(autouse=True)
def clean_preflight_state():
    """Reset preflight state between tests to prevent contamination."""
    reset_state()
    yield
    reset_state()