# -*- coding: utf-8 -*-
"""Gera graficos (PNG) a partir de results/results.csv para uso no TCC.

Saidas em results/:
  - grafico_tempo.png        (tempo medio de processamento por volume e estrategia)
  - grafico_throughput.png   (throughput medio por volume e estrategia)

Usa matplotlib. Cores sobrias e rotulos em portugues, adequados a um documento academico.
"""
import csv
import os
import statistics
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

IN_FILE = os.path.join("results", "results.csv")
OUT_DIR = "results"

LABELS = {"jpa": "JPA (ORM)", "native": "SQL nativo (batch)", "native-bulk": "SQL nativo (set-based)"}


def load():
    data = defaultdict(lambda: {"duration": [], "throughput": []})
    volumes, strategies = [], []
    with open(IN_FILE, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            vol, strat = int(row["volume"]), row["strategy"]
            dur = float(row["duration_ms"])
            data[(vol, strat)]["duration"].append(dur)
            # Throughput recalculado a partir de registros/tempo (consistente com analyze_results.py).
            data[(vol, strat)]["throughput"].append(float(row["total_records"]) / (dur / 1000.0) if dur else 0.0)
            if vol not in volumes:
                volumes.append(vol)
            if strat not in strategies:
                strategies.append(strat)
    volumes.sort()
    return data, volumes, strategies


def grouped_bar(data, volumes, strategies, metric, ylabel, title, outfile, divide=1.0):
    x = range(len(volumes))
    n = len(strategies)
    width = 0.8 / n
    fig, ax = plt.subplots(figsize=(8, 4.5))
    for i, strat in enumerate(strategies):
        means = []
        for vol in volumes:
            vals = data[(vol, strat)][metric]
            means.append(statistics.mean(vals) / divide if vals else 0)
        positions = [p + i * width - 0.4 + width / 2 for p in x]
        bars = ax.bar(positions, means, width, label=LABELS.get(strat, strat))
        ax.bar_label(bars, fmt="%.0f", fontsize=8, padding=2)
    ax.set_xticks(list(x))
    ax.set_xticklabels([f"{v:,}".replace(",", ".") for v in volumes])
    ax.set_xlabel("Volume de registros")
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend()
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    fig.tight_layout()
    fig.savefig(os.path.join(OUT_DIR, outfile), dpi=150)
    plt.close(fig)
    print("Gerado:", os.path.join(OUT_DIR, outfile))


def main():
    data, volumes, strategies = load()
    grouped_bar(
        data, volumes, strategies, "duration",
        "Tempo medio (ms)", "Tempo medio de processamento batch por estrategia",
        "grafico_tempo.png",
    )
    grouped_bar(
        data, volumes, strategies, "throughput",
        "Throughput medio (mil reg/s)", "Throughput medio por estrategia",
        "grafico_throughput.png", divide=1000.0,
    )


if __name__ == "__main__":
    main()
