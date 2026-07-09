#!/usr/bin/env python3
"""
Generator engine/src/main/resources/moves.json z danych Pokémon Showdown.

Pobiera https://play.pokemonshowdown.com/data/moves.json i konwertuje na nasz
schemat (Type/MoveCategory/MoveEffect). Efekty, których silnik jeszcze nie
modeluje, oznaczane są flagą "simplified": true — ruch ładuje się z podstawą,
ale jego pełne działanie nie jest odtworzone.

Uruchomienie:
  curl -s https://play.pokemonshowdown.com/data/moves.json -o /tmp/ps-moves.json
  python3 tools/gen_moves.py /tmp/ps-moves.json
(bez argumentu próbuje pobrać sam przez urllib — na macOS bywa problem z SSL,
wtedy użyj curl jak wyżej.)
"""
import json
import sys
import urllib.request

SRC = "https://play.pokemonshowdown.com/data/moves.json"
OUT = "engine/src/main/resources/moves.json"

# Nasze 18 typów.
TYPES = {"Normal", "Fire", "Water", "Grass", "Electric", "Ice", "Fighting",
         "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost",
         "Dragon", "Dark", "Steel", "Fairy"}

STATUS_MAP = {"brn": "BRN", "psn": "PSN", "tox": "TOX", "par": "PAR", "slp": "SLP", "frz": "FRZ"}
STAT_MAP = {"atk": "ATTACK", "def": "DEFENSE", "spa": "SPECIAL_ATTACK",
            "spd": "SPECIAL_DEFENSE", "spe": "SPEED"}

# Wykluczamy ruchy niestandardowe / gimmicki (Z, Max, CAP, fan-made, LGPE, przyszłe).
SKIP_NONSTANDARD = {"CAP", "Future", "Custom", "Gigantamax", "LGPE"}


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def pct(frac):
    """[n, d] -> procent 1..100."""
    return clamp(round(frac[0] / frac[1] * 100), 1, 100)


def convert(mid, m):
    """Zwraca (entry dict, ok) — ok=False gdy ruch pomijamy."""
    if m.get("isZ") or m.get("isMax"):
        return None
    if m.get("isNonstandard") in SKIP_NONSTANDARD:
        return None
    mtype = m.get("type")
    if mtype not in TYPES:
        return None
    cat = m.get("category")
    if cat not in ("Physical", "Special", "Status"):
        return None

    simplified = False
    effects = []

    def flag():
        nonlocal simplified
        simplified = True

    # --- primary status (Toxic, Thunder Wave, Will-O-Wisp) ---
    if m.get("status"):
        s = STATUS_MAP.get(m["status"])
        if s:
            effects.append({"kind": "inflictStatus", "status": s, "target": "OPPONENT", "chance": 100})
        else:
            flag()

    # --- primary boosts (Swords Dance SELF, Growl OPPONENT) ---
    if m.get("boosts"):
        tgt = "SELF" if m.get("target") == "self" else "OPPONENT"
        for stat, val in m["boosts"].items():
            if stat in STAT_MAP and val != 0:
                effects.append({"kind": "statChange", "stat": STAT_MAP[stat],
                                "stages": clamp(val, -6, 6), "target": tgt, "chance": 100})
            else:
                flag()

    # --- self.boosts (Close Combat) / inne self.* -> nieobsługiwane ---
    self_eff = m.get("self")
    if self_eff:
        if self_eff.get("boosts"):
            for stat, val in self_eff["boosts"].items():
                if stat in STAT_MAP and val != 0:
                    effects.append({"kind": "statChange", "stat": STAT_MAP[stat],
                                    "stages": clamp(val, -6, 6), "target": "SELF", "chance": 100})
                else:
                    flag()
        if any(k != "boosts" for k in self_eff):
            flag()  # mustrecharge, volatileStatus itd.

    # --- secondary (Flamethrower 10% burn, Body Slam 30% par) ---
    sec = m.get("secondary")
    if sec:
        chance = sec.get("chance", 100)
        consumed = {"chance"}
        if sec.get("status"):
            s = STATUS_MAP.get(sec["status"])
            if s:
                effects.append({"kind": "inflictStatus", "status": s, "target": "OPPONENT", "chance": chance})
            else:
                flag()
            consumed.add("status")
        if sec.get("boosts"):
            for stat, val in sec["boosts"].items():
                if stat in STAT_MAP and val != 0:
                    effects.append({"kind": "statChange", "stat": STAT_MAP[stat],
                                    "stages": clamp(val, -6, 6), "target": "OPPONENT", "chance": chance})
                else:
                    flag()
            consumed.add("boosts")
        if sec.get("volatileStatus") == "flinch":
            effects.append({"kind": "flinch", "chance": chance})
            consumed.add("volatileStatus")
        if any(k not in consumed for k in sec):
            flag()  # confusion, self, dustproof itd.
    if m.get("secondaries"):
        flag()  # wiele efektów naraz

    # --- drain / recoil / heal ---
    if m.get("drain"):
        effects.append({"kind": "drain", "percent": pct(m["drain"])})
    if m.get("recoil"):
        effects.append({"kind": "recoil", "percent": pct(m["recoil"])})
    if m.get("heal"):
        effects.append({"kind": "heal", "percent": pct(m["heal"]), "target": "SELF", "chance": 100})

    # --- hazard: tylko Stealth Rock na razie ---
    sc = m.get("sideCondition")
    if sc:
        if sc == "stealthrock":
            effects.append({"kind": "hazard", "condition": "STEALTH_ROCK"})
        else:
            flag()  # spikes, reflect, lightscreen... później

    # --- pivot ---
    if m.get("selfSwitch"):
        effects.append({"kind": "forceSelfSwitch"})

    # --- primary volatileStatus flinch (rzadkie; większość flinchów to secondary) ---
    prim_volatile = m.get("volatileStatus")
    if prim_volatile == "flinch":
        effects.append({"kind": "flinch", "chance": 100})
        prim_volatile = None  # obsłużone, nie flaguj niżej

    # --- pola jeszcze nieobsługiwane ---
    unsupported = {"multihit": m.get("multihit"), "ohko": m.get("ohko"),
                   "weather": m.get("weather"), "terrain": m.get("terrain"),
                   "volatileStatus": prim_volatile, "forceSwitch": m.get("forceSwitch"),
                   "damage": m.get("damage"), "pseudoWeather": m.get("pseudoWeather"),
                   "slotCondition": m.get("slotCondition")}
    for f, val in unsupported.items():
        if val is not None:
            flag()

    # Ruchy dwuturowe (charge: Solar Beam, Fly) i z odpoczynkiem (recharge: Hyper Beam).
    flags = m.get("flags", {})
    if flags.get("charge") or flags.get("recharge"):
        flag()

    acc = m.get("accuracy")
    accuracy = 100 if acc is True else int(acc)
    accuracy = clamp(accuracy, 1, 100)
    power = int(m.get("basePower", 0))
    if cat == "Status":
        power = 0  # nasz Move wymaga power==0 dla STATUS

    entry = {
        "name": m["name"],
        "type": mtype.upper(),
        "category": cat.upper(),
        "power": power,
        "accuracy": accuracy,
        "pp": max(1, int(m.get("pp", 1))),
        "priority": clamp(int(m.get("priority", 0)), -7, 5),
    }
    if effects:
        entry["effects"] = effects
    if simplified:
        entry["simplified"] = True
    return entry


def main():
    if len(sys.argv) > 1:
        print("Czytam", sys.argv[1], file=sys.stderr)
        with open(sys.argv[1], encoding="utf-8") as r:
            data = json.load(r)
    else:
        print("Pobieram", SRC, file=sys.stderr)
        with urllib.request.urlopen(SRC, timeout=30) as r:
            data = json.load(r)

    entries = []
    for mid, m in data.items():
        if not m.get("name") or "basePower" not in m and m.get("category") != "Status":
            pass
        e = convert(mid, m)
        if e:
            entries.append(e)

    entries.sort(key=lambda e: e["name"])
    seen = set()
    unique = []
    for e in entries:
        if e["name"] in seen:
            continue
        seen.add(e["name"])
        unique.append(e)

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("[\n")
        for i, e in enumerate(unique):
            comma = "," if i < len(unique) - 1 else ""
            f.write("  " + json.dumps(e, ensure_ascii=False) + comma + "\n")
        f.write("]\n")

    simp = sum(1 for e in unique if e.get("simplified"))
    print(f"Zapisano {len(unique)} ruchów do {OUT} ({simp} simplified, "
          f"{len(unique) - simp} w pełni obsługiwanych)", file=sys.stderr)


if __name__ == "__main__":
    main()
