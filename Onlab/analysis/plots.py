import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

import matplotlib

matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from matplotlib.lines import Line2D
from matplotlib.patches import Patch
from scipy import stats

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
DEFAULT_RESULTS = PROJECT / "results"
DEFAULT_GROUND_TRUTH = PROJECT / "src" / "main" / "resources" / "coverage" / "ground_truth.json"

TEXT_WIDTH_IN = 6.3
LANGS = ("hu", "en")
CATEGORIES = ("cond_pos", "cond_neg", "exp_pos", "exp_neg")
MARKERS = ("o", "s", "^", "D", "v", "P", "X", "*", "h", "<")
OUTCOMES = ("iter1", "iter2", "iter3", "failed")
OUTCOME_COLORS = {"iter1": "#1b7837", "iter2": "#7fbf7b", "iter3": "#d9f0d3", "failed": "#d6604d"}

TEXT = {
    "en": {
        "manual": "Manual suite (reference)",
        "llm": "LLM test scenarios",
        "generated": "Generated suite",
        "solver": "Solver only (ablation)",
        "coverage_pct": "Object coverage [%]",
        "corr_x": "Achievable maximum (manual suite) [items]",
        "corr_y": "Covered by the generated suite [items]",
        "ideal": "Ideal (y = x)",
        "fit": "Linear fit (LLM)",
        "requirement": "Requirement",
        "cond_pos": "Condition\nholds",
        "cond_neg": "Condition\nviolated",
        "exp_pos": "Expected\noutput",
        "exp_neg": "Forbidden\noutput",
        "category_y": "Covered items [%]",
        "iter1": "Valid at 1st attempt",
        "iter2": "Valid at 2nd attempt",
        "iter3": "Valid at 3rd attempt",
        "failed": "No valid answer",
        "share_runs": "Share of runs [%]",
        "tokens": "Tokens per run [×10³]",
        "scenarios": "Test scenarios per suite",
        "excluded": "Infeasible negative (excluded)",
        "manual_min": "Manual minimal suite",
        "heat_manual": "Manual",
        "heat_llm": "LLM",
        "heat_solver": "Solver\nonly",
        "na": "n/a",
        "table_header": ["Req.", "Difficulty", "Items", "Max", "Success", "Iter.", "Scen. (LLM / man.)",
                         "LLM cov. [\\%]", "Solver only [\\%]", "Tokens [k]"],
        "table_caption": "Benchmark summary per requirement. Max: coverage of the manual reference suite; "
                         "Iter.: mean number of LLM calls until a formally valid answer; coverage values are "
                         "the mean (and standard deviation) of the successful runs.",
    },
    "hu": {
        "manual": "Kézi készlet (referencia)",
        "llm": "LLM tesztszcenáriók",
        "generated": "Generált készlet",
        "solver": "Csak solver (abláció)",
        "coverage_pct": "Objektumlefedettség [%]",
        "corr_x": "Elérhető maximum (kézi készlet) [elem]",
        "corr_y": "A generált készlet által lefedett [elem]",
        "ideal": "Ideális (y = x)",
        "fit": "Lineáris illesztés (LLM)",
        "requirement": "Követelmény",
        "cond_pos": "Feltétel\nteljesül",
        "cond_neg": "Feltétel\nnem teljesül",
        "exp_pos": "Elvárt\nkimenet",
        "exp_neg": "Tiltott\nkimenet",
        "category_y": "Lefedett elemek [%]",
        "iter1": "Érvényes az 1. próbára",
        "iter2": "Érvényes a 2. próbára",
        "iter3": "Érvényes a 3. próbára",
        "failed": "Nincs érvényes válasz",
        "share_runs": "Futások aránya [%]",
        "tokens": "Token / futás [×10³]",
        "scenarios": "Tesztszcenáriók száma",
        "excluded": "Lehetetlen negatív (kizárva)",
        "manual_min": "Kézi minimális készlet",
        "heat_manual": "Kézi",
        "heat_llm": "LLM",
        "heat_solver": "Csak\nsolver",
        "na": "n. a.",
        "table_header": ["Köv.", "Nehézség", "Elem", "Max", "Siker", "Iter.", "Szcen. (LLM / kézi)",
                         "LLM lef. [\\%]", "Csak solver [\\%]", "Token [ezer]"],
        "table_caption": "A benchmark összesítése követelményenként. Max: a kézi referencia-készlet "
                         "lefedettsége; Iter.: a formálisan érvényes válaszig szükséges LLM-hívások átlagos "
                         "száma; a lefedettségek a sikeres futások átlagai (és szórásai).",
    },
}

DIFFICULTY_HU = {"Easy": "Könnyű", "Medium": "Közepes", "Hard": "Nehéz", "Extreme": "Extrém", "Complex": "Komplex"}

CAPTIONS = {
    "en": {
        "fig1": "Object coverage per requirement for the manual reference suite, the LLM-designed test scenarios "
                "and the solver-only ablation (generated constraints without scenarios). Bars: mean of the "
                "successful runs; dots: individual runs; numbers above the groups: successful/all runs; in "
                "brackets: number of coverage items.",
        "fig2": "Covered items of the generated suites versus the achievable maximum determined with the manual "
                "suite. Each point is one successful run; the dashed line is perfect agreement.",
        "fig3": "Coverage per coverage-item category (condition holds, condition violated, expected output, "
                "forbidden output), pooled over all requirements and successful runs.",
        "fig4": "(a) Outcome of the runs: the attempt at which Refinery accepted the answer of the LLM, or no "
                "valid answer after the last repair round. (b) Tokens consumed per run, summed over all repair "
                "rounds.",
        "fig5": "Number of test scenarios designed by the LLM per suite compared with the minimal manual suite.",
        "fig5x": "Number of test scenarios designed by the LLM per suite compared with the minimal manual suite. "
                 "Hatched: scenarios declared as negative cases that are unsatisfiable already in the system "
                 "model (without the generated constraints); they were excluded from the suite and are not "
                 "counted in the coverage.",
        "fig6": "Coverage items of {req}: the manual suite and the share of successful runs in which the "
                "generated suite (and the solver-only ablation) witnessed the item.",
    },
    "hu": {
        "fig1": "Követelményenkénti objektumlefedettség a kézi referencia-készlet, az LLM által tervezett "
                "tesztszcenáriók és a csak solveres abláció (generált megszorítások szcenáriók nélkül) esetén. "
                "Oszlop: a sikeres futások átlaga; pont: egyes futások; a csoportok fölött: sikeres/összes "
                "futás; zárójelben: a lefedettségi elemek száma.",
        "fig2": "A generált készletek által lefedett elemek száma a kézi készlettel meghatározott elérhető "
                "maximum függvényében. Minden pont egy sikeres futás; a szaggatott vonal a tökéletes egyezés.",
        "fig3": "Lefedettség elemkategóriánként (feltétel teljesül, feltétel nem teljesül, elvárt kimenet, "
                "tiltott kimenet), az összes követelményre és sikeres futásra összesítve.",
        "fig4": "(a) A futások kimenete: hányadik próbálkozásra fogadta el a Refinery az LLM válaszát, illetve "
                "az utolsó javítási kör után sincs érvényes válasz. (b) Futásonként felhasznált tokenek, az összes "
                "javítási körre összegezve.",
        "fig5": "Az LLM által tervezett tesztszcenáriók száma készletenként, a kézi minimális készlettel "
                "összevetve.",
        "fig5x": "Az LLM által tervezett tesztszcenáriók száma készletenként, a kézi minimális készlettel "
                 "összevetve. Sraffozva: a negatív esetként megjelölt szcenáriók, amelyek már a rendszermodellben "
                 "(a generált megszorítások nélkül) kielégíthetetlenek; ezek kikerültek a készletből, és a "
                 "lefedettségbe nem számítanak bele.",
        "fig6": "{req} lefedettségi elemei: a kézi készlet, valamint azon sikeres futások aránya, amelyekben a "
                "generált készlet (és a csak solveres abláció) tanúsította az elemet.",
    },
}


@dataclass
class Dataset:
    reqs: list
    items: dict
    runs: pd.DataFrame
    manual: dict
    difficulty: dict
    has_base: bool
    has_scenarios: bool
    benchmark_path: Path
    manual_path: Path

    def manual_set(self, req):
        return self.manual.get(req, {}).get("covered", set())


def split_ids(value):
    if value is None:
        return set()
    text = str(value).strip()
    if text in ("", "-", "n/a", "nan"):
        return set()
    return {part.strip() for part in text.split("|") if part.strip() and part.strip() != "-"}


def item_category(item):
    is_condition = str(item.get("traceTo", "")).startswith("C")
    negative = bool(item.get("negated")) or re.search(r"_C\d+N_|_EN_", item["id"]) is not None
    if is_condition:
        return "cond_neg" if negative else "cond_pos"
    return "exp_neg" if negative else "exp_pos"


def read_ground_truth(path):
    items = {}
    for entry in json.loads(path.read_text(encoding="utf-8")):
        for item in entry["items"]:
            item["category"] = item_category(item)
        items[entry["reqId"]] = entry["items"]
    return items


def read_benchmark(path):
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    header = [column.strip() for column in lines[0].split(",")]
    rows = []
    for line in lines[1:]:
        if not line.strip():
            continue
        # Az utolso oszlop (Error_Log) szabad szoveg: a maradek vesszok mind ebbe kerulnek.
        parts = line.split(",", len(header) - 1)
        rows.append(parts + [""] * (len(header) - len(parts)))
    raw = pd.DataFrame(rows, columns=header)

    def numeric(column):
        if column not in raw:
            return pd.Series(np.nan, index=raw.index)
        return pd.to_numeric(raw[column].str.strip(), errors="coerce")

    runs = pd.DataFrame({
        "req": raw["Req_ID"].str.strip(),
        "difficulty": raw["Difficulty"].str.strip() if "Difficulty" in raw else "",
        "run": numeric("Run_Index"),
        "success": raw["Success"].str.strip().str.lower() == "true",
        "iterations": numeric("Iterations"),
        "scenarios": numeric("Scenarios"),
        "infeasible": numeric("Infeasible_Negatives"),
        "tokens": numeric("Total_Tokens"),
        "covered_ids": raw["Covered_Item_Ids"].map(split_ids),
    })
    if "Base_Covered_Item_Ids" in raw:
        runs["base_ids"] = raw["Base_Covered_Item_Ids"].map(
            lambda value: None if value.strip() == "n/a" else split_ids(value))
    else:
        runs["base_ids"] = None
    return runs, "Scenarios" in raw


def read_manual(path):
    frame = pd.read_csv(path, dtype=str, keep_default_na=False)
    manual = {}
    for row in frame.itertuples(index=False):
        scenarios = str(getattr(row, "Scenarios", "")).strip()
        manual[row.Req_ID.strip()] = {
            "covered": split_ids(row.Covered_Item_Ids),
            "scenarios": int(scenarios) if scenarios.isdigit() else None,
        }
    return manual


def load_dataset(benchmark_path, manual_path, ground_truth_path):
    items = read_ground_truth(ground_truth_path)
    runs, has_scenarios = read_benchmark(benchmark_path)
    manual = read_manual(manual_path)

    reqs = [req for req in items if req in set(runs["req"])]
    missing = sorted(set(runs["req"]) - set(items))
    if missing:
        print(f"[plots] Figyelem: nincs ground truth ezekhez, kihagyva: {', '.join(missing)}")
    for req in reqs:
        if req not in manual:
            print(f"[plots] Figyelem: a kezi CSV-ben nincs {req}; futtasd ujra a 'manual' modot.")
    runs = runs[runs["req"].isin(reqs)].copy()

    universe = {req: {item["id"] for item in items[req]} for req in reqs}
    runs["total"] = runs["req"].map(lambda req: len(universe[req]))
    runs["covered"] = [len(ids & universe[req]) for ids, req in zip(runs["covered_ids"], runs["req"])]
    runs["coverage_pct"] = 100.0 * runs["covered"] / runs["total"]
    runs["base_covered"] = [np.nan if ids is None else len(ids & universe[req])
                            for ids, req in zip(runs["base_ids"], runs["req"])]
    runs["base_pct"] = 100.0 * runs["base_covered"] / runs["total"]
    runs["manual_covered"] = [len(manual.get(req, {}).get("covered", set()) & universe[req]) for req in runs["req"]]

    difficulty = runs.groupby("req")["difficulty"].first().to_dict()
    has_base = bool(runs["base_ids"].map(lambda ids: ids is not None).any())
    has_scenarios = has_scenarios and bool(runs["scenarios"].notna().any())
    return Dataset(reqs, items, runs, manual, difficulty, has_base, has_scenarios, benchmark_path, manual_path)


def latest(folder, pattern):
    candidates = sorted(folder.glob(pattern))
    return candidates[-1] if candidates else None


def tag_of(benchmark_path):
    match = re.fullmatch(r"benchmark_results_(.+)\.csv", benchmark_path.name)
    return match.group(1) if match else benchmark_path.stem


def fmt(value, lang, digits=2):
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return TEXT[lang]["na"]
    text = f"{value:.{digits}f}"
    return text.replace(".", ",") if lang == "hu" else text


def fmt_p(p, lang):
    if p is None or np.isnan(p):
        return TEXT[lang]["na"]
    if p < 0.001:
        return "p < " + fmt(0.001, lang, 3)
    return "p = " + fmt(p, lang, 3)


def difficulty_label(data, req, lang):
    difficulty = data.difficulty.get(req, "")
    return DIFFICULTY_HU.get(difficulty, difficulty) if lang == "hu" else difficulty


def req_label(data, req, lang):
    return f"{req}\n{difficulty_label(data, req, lang)} ({len(data.items[req])})"


def manual_covered(data, req):
    return len(data.manual_set(req) & {item["id"] for item in data.items[req]})


def correlation(x, y):
    x = np.asarray(x, dtype=float)
    y = np.asarray(y, dtype=float)
    result = {"n": int(len(x)), "pearson_r": np.nan, "pearson_p": np.nan, "spearman_rho": np.nan, "spearman_p": np.nan}
    # Konstans sorozatra (pl. minden kovetelmenyen ugyanannyi elem) a korrelacio nem ertelmezheto.
    if len(x) < 3 or np.ptp(x) == 0 or np.ptp(y) == 0:
        return result
    pearson = stats.pearsonr(x, y)
    spearman = stats.spearmanr(x, y)
    result.update(pearson_r=float(pearson.statistic), pearson_p=float(pearson.pvalue),
                  spearman_rho=float(spearman.statistic), spearman_p=float(spearman.pvalue))
    return result


def category_coverage(data):
    records = []
    ok = data.runs[data.runs["success"]]
    for req in data.reqs:
        manual_set = data.manual_set(req)
        for item in data.items[req]:
            records.append(("manual", item["category"], item["id"] in manual_set))
        for run in ok[ok["req"] == req].itertuples():
            for item in data.items[req]:
                records.append(("llm", item["category"], item["id"] in run.covered_ids))
                if run.base_ids is not None:
                    records.append(("solver", item["category"], item["id"] in run.base_ids))
    frame = pd.DataFrame(records, columns=["suite", "category", "covered"])
    return frame.groupby(["suite", "category"])["covered"].mean().mul(100).to_dict()


def compute_summary(data):
    runs = data.runs
    ok = runs[runs["success"]]
    with_base = ok[ok["base_covered"].notna()]

    def pooled_recall(column):
        hits, total = 0, 0
        for run in ok.itertuples():
            reference = data.manual_set(run.req)
            ids = getattr(run, column)
            if ids is None:
                continue
            hits += len(ids & reference)
            total += len(reference)
        return hits / total if total else np.nan

    return {
        "benchmark_csv": str(data.benchmark_path),
        "manual_csv": str(data.manual_path),
        "runs": int(len(runs)),
        "successful_runs": int(len(ok)),
        "success_rate": float(len(ok) / len(runs)) if len(runs) else np.nan,
        "mean_iterations_successful": float(ok["iterations"].mean()) if len(ok) else np.nan,
        "excluded_infeasible_negatives": int(ok["infeasible"].fillna(0).sum()),
        "runs_with_excluded_scenarios": int(ok["infeasible"].fillna(0).gt(0).sum()),
        "llm": {"pooled_recall_vs_manual": pooled_recall("covered_ids"),
                **correlation(ok["manual_covered"], ok["covered"])},
        "solver_only": {"pooled_recall_vs_manual": pooled_recall("base_ids"),
                        **correlation(with_base["manual_covered"], with_base["base_covered"])},
        "category_coverage_pct": {f"{suite}/{category}": value
                                  for (suite, category), value in category_coverage(data).items()},
    }


def set_style():
    sns.set_theme(context="paper", style="whitegrid", palette="colorblind")
    plt.rcParams.update({
        "font.family": "DejaVu Sans",
        "font.size": 8.5,
        "axes.titlesize": 9,
        "axes.labelsize": 8.5,
        "xtick.labelsize": 7.5,
        "ytick.labelsize": 7.5,
        "legend.fontsize": 7.5,
        "axes.spines.top": False,
        "axes.spines.right": False,
        "grid.linewidth": 0.5,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
        "savefig.dpi": 300,
    })


def suite_colors():
    palette = sns.color_palette("colorblind")
    return {"manual": "#8c8c8c", "llm": palette[0], "solver": palette[1]}


def suite_label(data, suite, lang):
    # A regi (szcenario nelkuli) CSV-ekben a generalt keszlet meg nem LLM-tervezett szcenariokbol all.
    if suite == "llm" and not data.has_scenarios:
        return TEXT[lang]["generated"]
    return TEXT[lang][suite]


def save(fig, out_dir, stem):
    for extension in ("pdf", "png"):
        fig.savefig(out_dir / f"{stem}.{extension}", bbox_inches="tight")
    plt.close(fig)


def figure_coverage(data, lang, out_dir):
    text = TEXT[lang]
    colors = suite_colors()
    ok = data.runs[data.runs["success"]]
    series = [
        ("manual", [[100.0 * manual_covered(data, req) / len(data.items[req])] for req in data.reqs]),
        ("llm", [ok.loc[ok["req"] == req, "coverage_pct"].tolist() for req in data.reqs]),
    ]
    if data.has_base:
        series.append(("solver", [ok.loc[(ok["req"] == req) & ok["base_pct"].notna(), "base_pct"].tolist()
                                  for req in data.reqs]))

    rng = np.random.default_rng(7)
    x = np.arange(len(data.reqs))
    width = 0.8 / len(series)
    fig, ax = plt.subplots(figsize=(TEXT_WIDTH_IN, 3.0))
    for k, (key, per_req) in enumerate(series):
        positions = x - 0.4 + width * (k + 0.5)
        means = [np.mean(values) if values else np.nan for values in per_req]
        ax.bar(positions, means, width * 0.9, color=colors[key], label=suite_label(data, key, lang),
               edgecolor="black", linewidth=0.4, zorder=2)
        for position, values in zip(positions, per_req):
            if len(values) > 1:
                ax.scatter(position + rng.uniform(-width * 0.2, width * 0.2, len(values)), values,
                           s=8, color="black", alpha=0.75, linewidths=0, zorder=3)
    for i, req in enumerate(data.reqs):
        runs = data.runs[data.runs["req"] == req]
        ax.text(x[i], 102, f"{int(runs['success'].sum())}/{len(runs)}", ha="center", va="bottom",
                fontsize=7, color="dimgray")
    ax.set_xticks(x, [req_label(data, req, lang) for req in data.reqs])
    ax.set_ylim(0, 112)
    ax.set_yticks(range(0, 101, 20))
    ax.set_ylabel(text["coverage_pct"])
    ax.grid(axis="x", visible=False)
    ax.legend(ncol=len(series), loc="lower center", bbox_to_anchor=(0.5, 1.0), frameon=False)
    save(fig, out_dir, "fig1_coverage_per_requirement")
    return "fig1_coverage_per_requirement", "fig1", "\\textwidth"


def figure_correlation(data, lang, out_dir, summary):
    text = TEXT[lang]
    colors = suite_colors()
    ok = data.runs[data.runs["success"]]
    if ok.empty:
        return None
    rng = np.random.default_rng(11)
    fig, ax = plt.subplots(figsize=(3.6, 3.4))
    for i, req in enumerate(data.reqs):
        sub = ok[ok["req"] == req]
        if sub.empty:
            continue
        marker = MARKERS[i % len(MARKERS)]
        jitter = rng.uniform(-0.3, 0.3, len(sub))
        ax.scatter(sub["manual_covered"] + jitter, sub["covered"], marker=marker, s=28,
                   color=colors["llm"], edgecolor="black", linewidth=0.4, zorder=3)
        base = sub[sub["base_covered"].notna()]
        if not base.empty:
            ax.scatter(base["manual_covered"] + rng.uniform(-0.3, 0.3, len(base)), base["base_covered"],
                       marker=marker, s=28, facecolors="none", edgecolors=colors["solver"], linewidth=0.9, zorder=2)

    limit = float(np.nanmax([ok["manual_covered"].max(), ok["covered"].max(), ok["base_covered"].max()])) + 2
    ax.plot([0, limit], [0, limit], ls="--", lw=0.8, color="gray", zorder=1)
    manual_x = ok["manual_covered"].to_numpy(dtype=float)
    if len(ok) >= 3 and np.ptp(manual_x) > 0:
        slope, intercept = np.polyfit(manual_x, ok["covered"].to_numpy(dtype=float), 1)
        ax.plot([0, limit], [intercept, intercept + slope * limit], lw=1.0, color=colors["llm"], zorder=1)
    ax.set_xlim(0, limit)
    ax.set_ylim(0, limit)
    ax.set_aspect("equal")
    ax.set_xlabel(text["corr_x"])
    ax.set_ylabel(text["corr_y"])

    lines = [suite_label(data, "llm", lang),
             f"  r = {fmt(summary['llm']['pearson_r'], lang)} ({fmt_p(summary['llm']['pearson_p'], lang)}), "
             f"ρ = {fmt(summary['llm']['spearman_rho'], lang)}"]
    if data.has_base:
        lines += [text["solver"],
                  f"  r = {fmt(summary['solver_only']['pearson_r'], lang)}, "
                  f"ρ = {fmt(summary['solver_only']['spearman_rho'], lang)}"]
    ax.text(0.03, 0.97, "\n".join(lines), transform=ax.transAxes, va="top", ha="left", fontsize=6.5,
            bbox={"boxstyle": "round,pad=0.3", "facecolor": "white", "edgecolor": "0.8"})

    handles = [Line2D([], [], ls="none", marker=MARKERS[i % len(MARKERS)], color="black", markerfacecolor="white",
                      markersize=5, label=req) for i, req in enumerate(data.reqs)]
    handles += [Line2D([], [], ls="none", marker="o", markerfacecolor=colors["llm"], markeredgecolor="black",
                       markersize=5, label=suite_label(data, "llm", lang))]
    if data.has_base:
        handles += [Line2D([], [], ls="none", marker="o", markerfacecolor="none", markeredgecolor=colors["solver"],
                           markersize=5, label=text["solver"])]
    handles += [Line2D([], [], ls="--", color="gray", lw=0.8, label=text["ideal"]),
                Line2D([], [], color=colors["llm"], lw=1.0, label=text["fit"])]
    ax.legend(handles=handles, loc="center left", bbox_to_anchor=(1.02, 0.5), frameon=False)
    save(fig, out_dir, "fig2_correlation")
    return "fig2_correlation", "fig2", "0.85\\textwidth"


def figure_categories(data, lang, out_dir):
    text = TEXT[lang]
    colors = suite_colors()
    coverage = category_coverage(data)
    suites = ["manual", "llm"] + (["solver"] if data.has_base else [])
    x = np.arange(len(CATEGORIES))
    width = 0.8 / len(suites)
    fig, ax = plt.subplots(figsize=(TEXT_WIDTH_IN * 0.8, 2.8))
    for k, suite in enumerate(suites):
        values = [coverage.get((suite, category), np.nan) for category in CATEGORIES]
        positions = x - 0.4 + width * (k + 0.5)
        ax.bar(positions, values, width * 0.9, color=colors[suite], label=suite_label(data, suite, lang),
               edgecolor="black", linewidth=0.4, zorder=2)
        for position, value in zip(positions, values):
            if not np.isnan(value):
                ax.text(position, value + 1.5, f"{value:.0f}", ha="center", va="bottom", fontsize=6.5)
    ax.set_xticks(x, [text[category] for category in CATEGORIES])
    ax.set_ylim(0, 112)
    ax.set_yticks(range(0, 101, 20))
    ax.set_ylabel(text["category_y"])
    ax.grid(axis="x", visible=False)
    ax.legend(ncol=len(suites), loc="lower center", bbox_to_anchor=(0.5, 1.0), frameon=False)
    save(fig, out_dir, "fig3_item_categories")
    return "fig3_item_categories", "fig3", "0.85\\textwidth"


def outcome(run):
    if not run.success:
        return "failed"
    iterations = 1 if np.isnan(run.iterations) else int(run.iterations)
    return "iter1" if iterations <= 1 else "iter2" if iterations == 2 else "iter3"


def figure_reliability(data, lang, out_dir):
    text = TEXT[lang]
    runs = data.runs.copy()
    runs["outcome"] = [outcome(run) for run in runs.itertuples()]
    x = np.arange(len(data.reqs))
    fig, (ax_outcome, ax_tokens) = plt.subplots(1, 2, figsize=(TEXT_WIDTH_IN, 2.8),
                                                gridspec_kw={"width_ratios": [1.2, 1]})
    bottom = np.zeros(len(data.reqs))
    for key in OUTCOMES:
        shares = np.array([100.0 * (runs.loc[runs["req"] == req, "outcome"] == key).mean() for req in data.reqs])
        ax_outcome.bar(x, shares, 0.7, bottom=bottom, color=OUTCOME_COLORS[key], label=text[key],
                       edgecolor="black", linewidth=0.4, zorder=2)
        bottom += shares
    ax_outcome.set_xticks(x, data.reqs, rotation=45, ha="right")
    ax_outcome.set_ylim(0, 100)
    ax_outcome.set_ylabel(text["share_runs"])
    ax_outcome.set_title("(a)", loc="left")
    ax_outcome.grid(axis="x", visible=False)
    ax_outcome.legend(loc="upper center", bbox_to_anchor=(0.5, -0.32), ncol=2, frameon=False)

    tokens = [runs.loc[runs["req"] == req, "tokens"].dropna().to_numpy() / 1000.0 for req in data.reqs]
    ax_tokens.bar(x, [values.mean() if len(values) else np.nan for values in tokens], 0.6,
                  color=suite_colors()["llm"], alpha=0.55, edgecolor="black", linewidth=0.4, zorder=2)
    rng = np.random.default_rng(3)
    for i, values in enumerate(tokens):
        ax_tokens.scatter(x[i] + rng.uniform(-0.15, 0.15, len(values)), values, s=8, color="black",
                          alpha=0.75, linewidths=0, zorder=3)
    ax_tokens.set_xticks(x, data.reqs, rotation=45, ha="right")
    ax_tokens.set_ylabel(text["tokens"])
    ax_tokens.set_title("(b)", loc="left")
    ax_tokens.grid(axis="x", visible=False)
    fig.tight_layout()
    save(fig, out_dir, "fig4_reliability_and_cost")
    return "fig4_reliability_and_cost", "fig4", "\\textwidth"


def figure_suite_size(data, lang, out_dir):
    ok = data.runs[data.runs["success"] & data.runs["scenarios"].notna()]
    if not data.has_scenarios or ok.empty:
        return None
    text = TEXT[lang]
    colors = suite_colors()
    x = np.arange(len(data.reqs))
    fig, ax = plt.subplots(figsize=(TEXT_WIDTH_IN * 0.75, 2.6))
    per_req = [ok.loc[ok["req"] == req, "scenarios"].to_numpy() for req in data.reqs]
    used = np.array([values.mean() if len(values) else np.nan for values in per_req])
    ax.bar(x, used, 0.6, color=colors["llm"], edgecolor="black", linewidth=0.4, zorder=2, label=text["llm"])
    excluded = np.array([ok.loc[ok["req"] == req, "infeasible"].fillna(0).mean() for req in data.reqs])
    has_excluded = bool(np.nan_to_num(excluded).any())
    if has_excluded:
        ax.bar(x, excluded, 0.6, bottom=np.nan_to_num(used), color="white", hatch="////", edgecolor="0.35",
               linewidth=0.4, zorder=2, label=text["excluded"])
    rng = np.random.default_rng(5)
    for i, values in enumerate(per_req):
        ax.scatter(x[i] + rng.uniform(-0.15, 0.15, len(values)), values, s=8, color="black", alpha=0.75,
                   linewidths=0, zorder=3)
    manual = [data.manual.get(req, {}).get("scenarios") for req in data.reqs]
    ax.scatter(x, [np.nan if value is None else value for value in manual], marker="D", s=30,
               color=colors["manual"], edgecolor="black", linewidth=0.5, zorder=4, label=text["manual_min"])
    ax.set_xticks(x, data.reqs)
    ax.set_ylabel(text["scenarios"])
    ax.yaxis.get_major_locator().set_params(integer=True)
    ax.set_ylim(bottom=0)
    ax.grid(axis="x", visible=False)
    ax.legend(loc="lower center", bbox_to_anchor=(0.5, 1.0), ncol=2, frameon=False)
    save(fig, out_dir, "fig5_suite_size")
    return "fig5_suite_size", "fig5x" if has_excluded else "fig5", "0.75\\textwidth"


def figure_item_heatmaps(data, lang, out_dir):
    text = TEXT[lang]
    ok = data.runs[data.runs["success"]]
    stems = []
    for req in data.reqs:
        items = data.items[req]
        runs_req = ok[ok["req"] == req]
        base_runs = [ids for ids in runs_req["base_ids"] if ids is not None]
        columns = [text["heat_manual"], text["heat_llm"]] + ([text["heat_solver"]] if data.has_base else [])
        manual_set = data.manual_set(req)
        values, labels = [], []
        for item in items:
            hit = item["id"] in manual_set
            row_values, row_labels = [1.0 if hit else 0.0], ["✓" if hit else "✗"]
            for id_sets in [list(runs_req["covered_ids"])] + ([base_runs] if data.has_base else []):
                hits = sum(item["id"] in ids for ids in id_sets)
                row_values.append(hits / len(id_sets) if id_sets else np.nan)
                row_labels.append(f"{hits}/{len(id_sets)}" if id_sets else "–")
            values.append(row_values)
            labels.append(row_labels)
        frame = pd.DataFrame(values, index=[re.sub(r"^I\d+_", "", item["id"]) for item in items], columns=columns)

        fig, ax = plt.subplots(figsize=(1.9 + 0.75 * len(columns), 0.24 * len(items) + 0.7))
        sns.heatmap(frame, annot=np.array(labels), fmt="", cmap="Blues", vmin=0, vmax=1, cbar=False,
                    linewidths=0.6, linecolor="white", mask=frame.isna(), annot_kws={"fontsize": 6.5}, ax=ax)
        ax.xaxis.tick_top()
        ax.tick_params(length=0)
        ax.set_xticklabels(ax.get_xticklabels(), rotation=0)
        ax.set_title(req, pad=16 if any("\n" in column for column in columns) else 10)
        stem = f"fig6_items_{req}"
        save(fig, out_dir, stem)
        stems.append((stem, req, len(items)))
    if not stems:
        return None
    largest = max(stems, key=lambda entry: entry[2])
    return largest[0], "fig6", "0.6\\textwidth", largest[1]


def requirement_summary(data):
    rows = []
    for req in data.reqs:
        runs = data.runs[data.runs["req"] == req]
        ok = runs[runs["success"]]
        rows.append({
            "Req_ID": req,
            "Difficulty": data.difficulty.get(req, ""),
            "Items": len(data.items[req]),
            "Manual_Max": manual_covered(data, req),
            "Manual_Scenarios": data.manual.get(req, {}).get("scenarios"),
            "Runs": len(runs),
            "Successful_Runs": len(ok),
            "Mean_Iterations": ok["iterations"].mean() if len(ok) else np.nan,
            "Mean_LLM_Scenarios": ok["scenarios"].mean() if data.has_scenarios and len(ok) else np.nan,
            "Excluded_Infeasible_Negatives": int(ok["infeasible"].fillna(0).sum()),
            "LLM_Coverage_Mean_Pct": ok["coverage_pct"].mean() if len(ok) else np.nan,
            "LLM_Coverage_Std_Pct": ok["coverage_pct"].std(ddof=1) if len(ok) > 1 else np.nan,
            "Solver_Only_Coverage_Mean_Pct": ok["base_pct"].mean() if data.has_base and len(ok) else np.nan,
            "Mean_Tokens": runs["tokens"].mean(),
        })
    return pd.DataFrame(rows)


def latex_table(summary, data, lang):
    text = TEXT[lang]
    rows = []
    for row in summary.itertuples(index=False):
        manual_scenarios = "–" if row.Manual_Scenarios is None or pd.isna(row.Manual_Scenarios) \
            else str(int(row.Manual_Scenarios))
        coverage = fmt(row.LLM_Coverage_Mean_Pct, lang, 1)
        if not pd.isna(row.LLM_Coverage_Std_Pct):
            coverage += " $\\pm$ " + fmt(row.LLM_Coverage_Std_Pct, lang, 1)
        cells = [
            row.Req_ID.replace("_", "\\_"),
            difficulty_label(data, row.Req_ID, lang),
            str(row.Items),
            str(row.Manual_Max),
            f"{row.Successful_Runs}/{row.Runs}",
            fmt(row.Mean_Iterations, lang, 1),
            f"{fmt(row.Mean_LLM_Scenarios, lang, 1)} / {manual_scenarios}",
            coverage,
            fmt(row.Solver_Only_Coverage_Mean_Pct, lang, 1),
            fmt(row.Mean_Tokens / 1000.0, lang, 1),
        ]
        rows.append(" & ".join(cells) + " \\\\")
    return "\n".join([
        "% \\usepackage{booktabs}",
        "\\begin{table}[htbp]",
        "  \\centering",
        "  \\small",
        f"  \\caption{{{text['table_caption']}}}",
        "  \\label{tab:benchmark-summary}",
        "  \\begin{tabular}{llrrrrcrrr}",
        "    \\toprule",
        "    " + " & ".join(text["table_header"]) + " \\\\",
        "    \\midrule",
        *["    " + row for row in rows],
        "    \\bottomrule",
        "  \\end{tabular}",
        "\\end{table}",
        "",
    ])


def figures_tex(entries, lang):
    lines = ["% \\usepackage{graphicx}; \\graphicspath{{<path to this folder>/}}", ""]
    for stem, key, width, *extra in entries:
        caption = CAPTIONS[lang][key].format(req=extra[0] if extra else "")
        label = stem.replace("_", "-")
        lines += [
            "\\begin{figure}[htbp]",
            "  \\centering",
            f"  \\includegraphics[width={width}]{{{stem}}}",
            f"  \\caption{{{caption}}}",
            f"  \\label{{fig:{label}}}",
            "\\end{figure}",
            "",
        ]
    return "\n".join(lines)


def to_jsonable(value):
    if isinstance(value, dict):
        return {key: to_jsonable(inner) for key, inner in value.items()}
    if isinstance(value, float) and np.isnan(value):
        return None
    if isinstance(value, (np.floating, np.integer)):
        return to_jsonable(value.item())
    return value


def main():
    parser = argparse.ArgumentParser(description="Szakdolgozatba illo abrak es tablazat a benchmark eredmenyeibol.")
    parser.add_argument("--benchmark", type=Path, help="benchmark_results_*.csv (alapertelmezes: a legfrissebb)")
    parser.add_argument("--manual", type=Path, help="manual_coverage_*.csv (alapertelmezes: a legfrissebb)")
    parser.add_argument("--ground-truth", type=Path, default=DEFAULT_GROUND_TRUTH)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--out", type=Path, help="kimeneti mappa (alapertelmezes: results/figures_<idobelyeg>)")
    args = parser.parse_args()

    benchmark = args.benchmark or latest(args.results, "benchmark_results_*.csv")
    manual = args.manual or latest(args.results, "manual_coverage_*.csv")
    if benchmark is None or manual is None:
        print(f"[plots] Hianyzo bemenet a(z) {args.results} mappaban: futtasd elobb a 'manual' es a 'benchmark' "
              f"modot.", file=sys.stderr)
        return 2
    out_dir = args.out or args.results / f"figures_{tag_of(benchmark)}"

    data = load_dataset(benchmark, manual, args.ground_truth)
    if not data.reqs:
        print("[plots] A benchmark CSV-ben nincs a ground truth-ban szereplo kovetelmeny.", file=sys.stderr)
        return 2
    set_style()
    summary = compute_summary(data)
    table = requirement_summary(data)

    out_dir.mkdir(parents=True, exist_ok=True)
    for lang in LANGS:
        lang_dir = out_dir / lang
        lang_dir.mkdir(exist_ok=True)
        entries = [
            figure_coverage(data, lang, lang_dir),
            figure_correlation(data, lang, lang_dir, summary),
            figure_categories(data, lang, lang_dir),
            figure_reliability(data, lang, lang_dir),
            figure_suite_size(data, lang, lang_dir),
            figure_item_heatmaps(data, lang, lang_dir),
        ]
        (lang_dir / "figures.tex").write_text(figures_tex([entry for entry in entries if entry], lang),
                                              encoding="utf-8")
        (lang_dir / "table_summary.tex").write_text(latex_table(table, data, lang), encoding="utf-8")

    table.to_csv(out_dir / "table_summary.csv", index=False)
    (out_dir / "summary.json").write_text(json.dumps(to_jsonable(summary), indent=2, ensure_ascii=False),
                                          encoding="utf-8")

    with pd.option_context("display.width", 160, "display.max_columns", 20, "display.precision", 1):
        print(table.drop(columns=["Difficulty"]).to_string(index=False))
    llm, solver = summary["llm"], summary["solver_only"]
    print(f"\nSikeres futasok: {summary['successful_runs']}/{summary['runs']}")
    print(f"LLM:         recall = {fmt(llm['pooled_recall_vs_manual'], 'en', 3)}, "
          f"Pearson r = {fmt(llm['pearson_r'], 'en', 3)} ({fmt_p(llm['pearson_p'], 'en')}), "
          f"Spearman rho = {fmt(llm['spearman_rho'], 'en', 3)}")
    if data.has_base:
        print(f"Csak solver: recall = {fmt(solver['pooled_recall_vs_manual'], 'en', 3)}, "
              f"Pearson r = {fmt(solver['pearson_r'], 'en', 3)}, "
              f"Spearman rho = {fmt(solver['spearman_rho'], 'en', 3)}")
    print(f"\nAbrak es tablazatok: {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
