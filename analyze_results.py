# -*- coding: utf-8 -*-
"""Agrega results/results.csv em media, desvio-padrao e ganho relativo por cenario.

Gera:
  - results/results_summary.csv  (tabela agregada)
  - impressao em Markdown no terminal (pronto para colar no TCC)

Usa apenas a biblioteca padrao (sem dependencias externas).
"""
import csv
import os
import statistics
from collections import defaultdict

IN_FILE = os.path.join("results", "results.csv")
OUT_FILE = os.path.join("results", "results_summary.csv")


def main():
    if not os.path.exists(IN_FILE):
        raise SystemExit(f"Arquivo nao encontrado: {IN_FILE}. Rode run-experiments.ps1 primeiro.")

    # (volume, strategy) -> {"duration": [...], "throughput": [...]}
    data = defaultdict(lambda: {"duration": [], "throughput": []})
    volumes = []
    strategies = []

    with open(IN_FILE, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            vol = int(row["volume"])
            strat = row["strategy"]
            duration_ms = float(row["duration_ms"])
            data[(vol, strat)]["duration"].append(duration_ms)
            # Throughput recalculado a partir de registros/tempo (definicao canonica e robusta).
            records = float(row["total_records"])
            data[(vol, strat)]["throughput"].append(records / (duration_ms / 1000.0) if duration_ms else 0.0)
            if vol not in volumes:
                volumes.append(vol)
            if strat not in strategies:
                strategies.append(strat)

    volumes.sort()

    # ---- CSV agregado ----
    rows = []
    for vol in volumes:
        for strat in strategies:
            key = (vol, strat)
            if key not in data:
                continue
            d = data[key]["duration"]
            t = data[key]["throughput"]
            rows.append({
                "volume": vol,
                "strategy": strat,
                "n": len(d),
                "duration_ms_mean": round(statistics.mean(d), 2),
                "duration_ms_std": round(statistics.pstdev(d), 2) if len(d) > 1 else 0.0,
                "throughput_mean": round(statistics.mean(t), 2),
                "throughput_std": round(statistics.pstdev(t), 2) if len(t) > 1 else 0.0,
            })

    with open(OUT_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)

    # ---- Markdown: tempo medio (ms) +- desvio por estrategia ----
    print(f"\nResumo salvo em {OUT_FILE}\n")
    print("Tempo medio de processamento (ms) - menor e melhor\n")
    header = "| Volume | " + " | ".join(strategies) + " |"
    sep = "|" + "---|" * (len(strategies) + 1)
    print(header)
    print(sep)
    for vol in volumes:
        cells = [f"{vol:,}".replace(",", ".")]
        for strat in strategies:
            key = (vol, strat)
            if key in data:
                d = data[key]["duration"]
                mean = statistics.mean(d)
                std = statistics.pstdev(d) if len(d) > 1 else 0.0
                cells.append(f"{mean:,.0f} ± {std:,.0f}".replace(",", "."))
            else:
                cells.append("-")
        print("| " + " | ".join(cells) + " |")

    # ---- Ganho relativo do SQL nativo frente ao JPA ----
    if "jpa" in strategies and "native" in strategies:
        print("\nGanho do SQL nativo (batch) frente ao JPA\n")
        print("| Volume | JPA (ms) | Native (ms) | Reducao de tempo | Speed-up |")
        print("|---|---|---|---|---|")
        for vol in volumes:
            kj, kn = (vol, "jpa"), (vol, "native")
            if kj in data and kn in data:
                mj = statistics.mean(data[kj]["duration"])
                mn = statistics.mean(data[kn]["duration"])
                reducao = (1 - mn / mj) * 100 if mj else 0
                speedup = mj / mn if mn else 0
                v = f"{vol:,}".replace(",", ".")
                print(f"| {v} | {mj:,.0f} | {mn:,.0f} | {reducao:.1f}% | {speedup:.2f}x |".replace(",", "."))

    print()


if __name__ == "__main__":
    main()
