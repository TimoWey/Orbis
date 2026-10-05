"""Dimension arithmetic and statistical planning; NOT a world simulation."""
import json
import math

rows = []
for width in (128, 256, 512, 1024, 2048, 4096):
    rows.append({
        "overworld_chunks_per_axis": width,
        "overworld_blocks_per_axis": width * 16,
        "overworld_total_chunks": width * width,
        "nether_chunks_per_axis": width // 8,
        "nether_blocks_per_axis": width * 2,
        "nether_total_chunks": (width // 8) ** 2,
    })
trials = []
for target in (0.95, 0.99, 0.999):
    n = math.ceil(math.log(0.05) / math.log(target))
    trials.append({
        "target_joint_success": target,
        "independent_zero_failure_trials": n,
        "one_sided_confidence": 0.95,
        "exact_success_lower_bound": 0.05 ** (1 / n),
    })
print(json.dumps({
    "real_world_simulation_executed": False,
    "candidate_sizes": rows,
    "validation_trial_plan": trials,
}, indent=2))
