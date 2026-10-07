"""Dimension arithmetic and statistical planning; NOT a world simulation."""
import json
import math
from pathlib import Path

spec = json.loads((Path(__file__).resolve().parents[1] / 'spec/world-presets.json').read_text(encoding='utf-8'))
rows = []
for preset in spec['presets']:
    width = preset['overworld_width_chunks']
    rows.append({
        "preset": preset['id'],
        "overworld_chunks_per_axis": width,
        "overworld_blocks_per_axis": width * 16,
        "overworld_total_chunks": width * width,
        "nether_chunks_per_axis": width // spec['nether_scale'],
        "nether_blocks_per_axis": width * 16 // spec['nether_scale'],
        "nether_total_chunks": (width // spec['nether_scale']) ** 2,
        "overworld_area_km2": (width * 16) ** 2 / 1_000_000,
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
