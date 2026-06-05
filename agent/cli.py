"""CLI trigger for the agent loop — runs one cycle manually or on schedule."""

import argparse
import sys
from graph import get_compiled_graph, AgentState


def run_once(region: str) -> dict:
    """Execute one agent loop cycle."""
    graph = get_compiled_graph()
    initial: AgentState = {"region": region, "top_k": []}
    result = graph.invoke(initial)
    return result


def main():
    parser = argparse.ArgumentParser(description="Hot rank agent loop trigger")
    parser.add_argument("--region", default="CN", help="Region to operate on")
    parser.add_argument("--repeat", type=int, default=1, help="Number of cycles to run")
    parser.add_argument("--interval", type=int, default=60, help="Seconds between cycles (if repeat > 1)")
    args = parser.parse_args()

    import time

    for i in range(args.repeat):
        print(f"[Cycle {i+1}/{args.repeat}] Running agent loop for region={args.region}...")
        result = run_once(args.region)
        decision = result.get("decision")
        effect = result.get("effect")
        if decision:
            print(f"  Decision: boost {decision['target']} weight={decision['weight']}")
        else:
            print("  Decision: no action (empty rankings)")
        if effect:
            print(f"  Effect: {effect}")
        print()

        if i < args.repeat - 1:
            time.sleep(args.interval)

    print("Done.")


if __name__ == "__main__":
    main()