#!/usr/bin/env python3
"""
Generator engine/src/main/resources/pokedex.json z danych Pokémon Showdown.

Łączy dwa źródła: pokedex.json (typy + bazowe staty) i learnsets.json
(legalne ruchy). Learnset jest przefiltrowany do ruchów, które faktycznie
mamy w moves.json — dzięki temu żaden gatunek nie wskazuje na ruch, którego
MoveDex nie zna, i walidacja movesetu po stronie serwera nie musi tego
sprawdzać drugi raz.

Uruchomienie:
  curl -s https://play.pokemonshowdown.com/data/pokedex.json   -o /tmp/ps-pokedex.json
  curl -s https://play.pokemonshowdown.com/data/learnsets.json -o /tmp/ps-learnsets.json
  python3 tools/gen_pokedex.py /tmp/ps-pokedex.json /tmp/ps-learnsets.json

Bez argumentów pobiera sam przez urllib (jak gen_moves.py).

Zawężenie do podzbioru (np. 20 monów z docs/concept.md):
  python3 tools/gen_pokedex.py ... --only Charizard,Blastoise,Venusaur
"""
import json
import re
import sys
import urllib.request

SRC_DEX = "https://play.pokemonshowdown.com/data/pokedex.json"
SRC_LEARNSETS = "https://play.pokemonshowdown.com/data/learnsets.json"
MOVES = "engine/src/main/resources/moves.json"
OUT = "engine/src/main/resources/pokedex.json"

# Nasze 18 typów (te same co w gen_moves.py).
TYPES = {"Normal", "Fire", "Water", "Grass", "Electric", "Ice", "Fighting",
         "Poison", "Ground", "Flying", "Psychic", "Bug", "Rock", "Ghost",
         "Dragon", "Dark", "Steel", "Fairy"}

# Gatunki niestandardowe / spoza gier głównej serii.
SKIP_NONSTANDARD = {"CAP", "Future", "Custom", "Gigantamax", "LGPE", "Unobtainable"}

# Mapowanie statów Showdown -> pola naszego rekordu Stats.
STAT_KEYS = {"hp": "hp", "atk": "attack", "def": "defense",
             "spa": "specialAttack", "spd": "specialDefense", "spe": "speed"}


def to_id(name):
    """Nazwa wyświetlana -> id Showdown ('Will-O-Wisp' -> 'willowisp')."""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def load_json(path_or_url, is_url):
    if is_url:
        print("Pobieram", path_or_url, file=sys.stderr)
        with urllib.request.urlopen(path_or_url, timeout=60) as r:
            return json.load(r)
    print("Czytam", path_or_url, file=sys.stderr)
    with open(path_or_url, encoding="utf-8") as r:
        return json.load(r)


def load_known_moves():
    """id ruchu -> nazwa wyświetlana, na podstawie NASZEGO moves.json.

    Klucz filtrowania learnsetów. Gdyby brać nazwy z danych Showdown, dex
    wskazywałby na ruchy, których MoveDex nie ma, i get() by rzucał.
    """
    with open(MOVES, encoding="utf-8") as f:
        return {to_id(m["name"]): m["name"] for m in json.load(f)}


def convert(sid, s, learnsets, known_moves):
    """Zwraca wpis dexu albo None, gdy gatunek pomijamy."""
    if s.get("isNonstandard") in SKIP_NONSTANDARD:
        return None
    # Formy alternatywne (mega, regionalne, Gmax) mają baseSpecies/forme.
    # MVP modeluje tylko formy bazowe — bez itemów i abilities mega i tak
    # nie ma jak zadziałać.
    if s.get("forme") or s.get("baseSpecies"):
        return None
    if int(s.get("num", 0)) <= 0:
        return None  # Missingno i inne wpisy techniczne

    types = s.get("types") or []
    if not types or any(t not in TYPES for t in types):
        return None

    base_stats = s.get("baseStats") or {}
    if any(k not in base_stats for k in STAT_KEYS):
        return None
    base = {field: int(base_stats[key]) for key, field in STAT_KEYS.items()}
    if any(v <= 0 for v in base.values()):
        return None  # nasz rekord Stats wymaga wartości dodatnich

    entry_ls = learnsets.get(sid) or {}
    move_ids = (entry_ls.get("learnset") or {}).keys()
    learnset = sorted({known_moves[mid] for mid in move_ids if mid in known_moves})

    entry = {
        "id": sid,
        "name": s["name"],
        "num": int(s["num"]),
        "primary": types[0].upper(),
    }
    if len(types) > 1:
        entry["secondary"] = types[1].upper()
    entry["base"] = base
    entry["learnset"] = learnset
    return entry


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only_arg = next((a for a in sys.argv[1:] if a.startswith("--only=")), None)
    only = None
    if only_arg:
        only = {to_id(n) for n in only_arg.split("=", 1)[1].split(",") if n.strip()}

    dex = load_json(args[0], False) if len(args) > 0 else load_json(SRC_DEX, True)
    learnsets = load_json(args[1], False) if len(args) > 1 else load_json(SRC_LEARNSETS, True)
    known_moves = load_known_moves()

    entries = []
    for sid, s in dex.items():
        if only is not None and sid not in only:
            continue
        e = convert(sid, s, learnsets, known_moves)
        if e:
            entries.append(e)

    entries.sort(key=lambda e: e["num"])

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("[\n")
        for i, e in enumerate(entries):
            comma = "," if i < len(entries) - 1 else ""
            f.write("  " + json.dumps(e, ensure_ascii=False) + comma + "\n")
        f.write("]\n")

    empty = sum(1 for e in entries if not e["learnset"])
    avg = sum(len(e["learnset"]) for e in entries) / max(1, len(entries))
    print(f"Zapisano {len(entries)} gatunków do {OUT} "
          f"(średnio {avg:.0f} ruchów w learnsecie, {empty} bez learnsetu)",
          file=sys.stderr)


if __name__ == "__main__":
    main()
