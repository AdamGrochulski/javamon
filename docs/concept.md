# Javamon — Concept Document

## Wizja projektu

Przeglądarkowy symulator walk Pokémonów zbudowany od zera — inspirowany Pokemon Showdown, ale własna implementacja silnika walk, własny protokół WebSocket i własny backend w Javie. Celem nie jest klon PS, tylko zrozumienie i zbudowanie każdej warstwy samodzielnie.

**Pokemon Showdown** jako punkt odniesienia:
- Walki 1v1 w czasie rzeczywistym przez przeglądarkę
- Drużyny 6 Pokémonów, wybór przed walką
- Animowany log walki (każdy ruch krok po kroku)
- Replay po zakończeniu walki
- Rankingi / ladder system

Nasz zakres: **uproszczona mechanika współczesna** (18 typów, podział physical/special per ruch, osobne Sp.Atk/Sp.Def) na małym zestawie danych — solidny silnik zamiast pełnego Pokédexu, rozszerzalny później.

---

## Architektura

```
┌─────────────────────────────────────────────────────┐
│  React Frontend (TypeScript)                        │
│  - Team builder UI                                  │
│  - Battle screen (animowany log akcji)              │
│  - Ranking / historia walk                          │
└──────────────────────┬──────────────────────────────┘
                       │ REST + WebSocket
┌──────────────────────▼──────────────────────────────┐
│  Spring Boot Backend (Java)                         │
│  - REST API: trenerzy, drużyny, historia walk       │
│  - WebSocket: sesje walk w czasie rzeczywistym      │
│  - Battle Engine: czysty Java, bez frameworka       │
└──────────┬────────────────────────┬─────────────────┘
           │                        │
┌──────────▼──────┐      ┌──────────▼──────┐
│  PostgreSQL     │      │  Redis           │
│  - konta        │      │  - stan aktywnej │
│  - drużyny      │      │    sesji walki   │
│  - historia     │      │  - matchmaking   │
│  - replaye      │      │    queue         │
└─────────────────┘      └─────────────────┘
```

Deploy: Docker Compose na serwerze `octo`, dostępne przez subdomenę przez Nginx Proxy Manager.

---

## Battle Engine — uproszczona mechanika współczesna

### Typy i efektywność

18 typów, macierz efektywności `float[18][18]`:
- Super effective: 2.0
- Not very effective: 0.5
- Immune: 0.0
- Normal: 1.0

### Staty Pokémona

```
HP, Attack, Defense, Sp.Attack, Sp.Defense, Speed
+ aktualne HP (w trakcie walki)
+ modyfikatory statów: -6 do +6 (Swords Dance, Growl, itp.)
```

### Statusy

| Status | Efekt |
|--------|-------|
| BRN | -50% Attack, -1/16 HP co turę |
| PSN | -1/8 HP co turę |
| PAR | -50% Speed, 25% szansa na pominięcie tury |
| TOX | (Toxic) eskalujące obrażenia: -1/16, -2/16, -3/16... HP co turę |
| SLP | 1–3 tury bez możliwości ataku |
| FRZ | nie może atakować, 20% szansa na odtajanie |

> **Stan (Faza 1):** zaimplementowany tick końca tury (BRN 1/16, PSN 1/8, TOX eskalujący). Blokada ruchu (SLP/FRZ/PAR) i modyfikacja statów (BRN -atak, PAR -speed) — do domknięcia z pełną integracją w resolverze.

### Formuła obrażeń (uproszczona)

```
Damage = ((2 * Level / 5 + 2) * Power * (Atk / Def) / 50 + 2)
         * TypeEffectiveness
         * STAB        (1.5 jeśli typ ruchu = typ Pokémona)
         * CriticalHit (1.5 przy krytyku, ~6% szansa)
         * Random      (0.85–1.0)
```

Cała losowość (krytyk, random roll, celność, speed tie) idzie przez wstrzykiwany interfejs `Rng` — testy dostają fake/seeded RNG i liczą wynik deterministycznie. Kolejność wywołań RNG jest ustalona (accuracy → crit → random).

### Celność i PP

- Każdy ruch ma **accuracy** (np. Thunder 70%, Hydro Pump 80%, Blizzard 70%) — rzut na trafienie przed obliczeniem obrażeń; pudło = event `MOVE_MISSED`.
- Każdy ruch ma **PP** — zużywane przy użyciu, PP = 0 → ruch niedostępny (serwer waliduje).

### Turn order

1. Wyższy priority idzie pierwszy (Quick Attack: +1, Tackle: 0)
2. Przy tym samym priority — wyższy Speed
3. Remis Speed → losowo

---

## State Machine walki

```
BattleSession {
  id: UUID
  player1: TrainerState
  player2: TrainerState
  turn: int
  phase: CHOOSE_ACTION | RESOLVE_TURN | SWITCH | GAME_OVER
  log: List<BattleEvent>
}

TrainerState {
  trainer: Trainer
  team: List<BattlePokemon>   // 6 pokemon z aktualnymi HP/statusem
  activePokemonIndex: int
  selectedAction: Action      // null jeśli jeszcze nie wybrał
}

Action {
  type: MOVE | SWITCH | FORFEIT
  moveId?: int
  switchTargetIndex?: int
}
```

**Przebieg tury:**
1. Obaj gracze wysyłają akcję przez WebSocket
2. Serwer czeka na obu (timeout 30s → auto-forfeit)
3. Resolve: priority sort → speed sort → execute → check faint → check win
4. End-of-turn effects (PSN/BRN damage)
5. Jeśli ktoś zemdlał: faza SWITCH
6. Broadcast `BattleEvent[]` do obu graczy

---

## WebSocket API

**Endpoint:** `ws://host/battle/{sessionId}`

**Klient → Serwer:**
```json
{ "type": "ACTION", "payload": { "type": "MOVE", "moveId": 5 } }
{ "type": "ACTION", "payload": { "type": "SWITCH", "targetIndex": 2 } }
{ "type": "ACTION", "payload": { "type": "FORFEIT" } }
```

**Serwer → Klient:**
```json
{ "type": "MOVE_USED", "attacker": "Charizard", "move": "Flamethrower", "target": "Blastoise" }
{ "type": "MOVE_MISSED", "attacker": "Charizard", "move": "Flamethrower" }
{ "type": "DAMAGE", "target": "Blastoise", "amount": 87, "hpLeft": 123, "hpMax": 310, "crit": false, "effectiveness": 2.0 }
{ "type": "NO_EFFECT", "target": "Gengar" }
{ "type": "STATUS_TICK", "target": "Blastoise", "status": "PSN", "amount": 20, "hpLeft": 103 }
{ "type": "SWITCH", "player": "player1", "out": "Charizard", "in": "Blastoise" }
{ "type": "FAINTED", "pokemon": "Blastoise" }
{ "type": "GAME_OVER", "winner": "player1" }
{ "type": "TURN_START", "turn": 4 }
```

Zestaw odpowiada eventom silnika (`BattleEvent`: `MoveUsed`, `MoveMissed`, `Damage`, `NoEffect`, `StatusTick`, `Switch`, `Faint`, `Forfeit`, `BattleEnd`) — warstwa WS mapuje je na JSON. Frontend odtwarza sekwencyjnie z animacją (jak Pokemon Showdown robi swój battle log).

---

## REST API

```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/guest             -- konto gościa (gra bez rejestracji)

GET    /api/pokemon                  -- lista wszystkich Pokémonów
GET    /api/pokemon/{id}/moves       -- dostępne ruchy dla Pokémona

GET    /api/teams                    -- moje drużyny
POST   /api/teams                    -- utwórz drużynę (6 Pokémonów + movesety)
PUT    /api/teams/{id}
DELETE /api/teams/{id}

POST   /api/matchmaking/join         -- dołącz do kolejki (Redis)
DELETE /api/matchmaking/leave

GET    /api/battles/{id}             -- wynik + replay (lista eventów)
GET    /api/battles/history          -- historia walk zalogowanego trenera

GET    /api/leaderboard              -- ranking ELO top 20
```

ELO rating (K=32), aktualizowany po każdej zakończonej walce.

---

## MVP — dane (zestaw startowy)

**~20 Pokémonów** pokrywających wszystkie typy i archetypy (fast attacker, bulky wall, setup sweeper):
Charizard, Blastoise, Venusaur, Pikachu, Gengar, Alakazam, Machamp, Golem, Lapras, Snorlax, Dragonite, Scizor, Tyranitar, Gardevoir, Lucario, Garchomp, Gyarados, Togekiss, Ferrothorn, Jolteon

**~30 ruchów** pokrywających wszystkie typy, fizyczne + specjalne + statusowe:
Tackle, Body Slam, Flamethrower, Fire Blast, Surf, Hydro Pump, Thunderbolt, Thunder, Ice Beam, Blizzard, Earthquake, Psychic, Shadow Ball, Dragon Claw, Iron Head, Close Combat, Poison Jab, Quick Attack, Hyper Beam, Swords Dance, Growl, Recover, Will-O-Wisp, Thunder Wave, Toxic, Spore, Stealth Rock, U-turn, Volt Switch, Dragon Dance

---

## Stack technologiczny

| Warstwa | Technologia |
|---------|------------|
| Backend | Java 21, Spring Boot 3, Spring Security (JWT) |
| WebSocket | Spring WebSocket (raw `WebSocketHandler`, własny protokół JSON — bez STOMP) |
| Baza danych | PostgreSQL (JPA/Hibernate) |
| Cache / session | Redis (Lettuce) |
| Frontend | React + TypeScript (Vite) |
| Deploy | Docker Compose, Nginx, Let's Encrypt TLS |
| Serwer | `octo`, subdomena np. `battle.adamgrochulski.dev` |

---

## Plan pracy — fazy

### Faza 1: Core engine (tydzień 1–2) — ✅ ukończona

Silnik: czysty Java, moduł Maven, pakiety `model` / `rng` / `damage` / `battle`. 70 testów jednostkowych (bez Springa).

- [x] Model danych: Type, Move (+priority/PP), Stats/BattleStats, BattlePokemon, Battle/BattleSide
- [x] Macierz efektywności typów (data-driven, `type-chart.json` + Jackson)
- [x] Formuła obrażeń + STAB + krytyk + random (wstrzykiwany `Rng`, `DamageResult`)
- [x] Status effects — apply + tick (BRN/PSN/TOX); *blokada ruchu i mod statów odłożone*
- [x] Turn resolver: kolejność (switch>move, priority→speed→RNG), wykonanie MOVE/SWITCH/FORFEIT, ticki, wynik
- [x] Unit testy silnika (bez Spring)
- [x] Eventy walki (`BattleEvent`) — podstawa renderu i replay
- [x] Wymuszony switch po faincie (`needsReplacement`/`awaitingReplacement` + `resolveReplacement`)

**Do domknięcia w Fazie 1.5 / przy integracji:** walidacja akcji względem stanu po stronie serwera, efekty ruchów statusowych, entry hazardy, złożone akcje (U-turn/Volt Switch), moves.json + loader.

### Faza 2: Backend API (tydzień 2–3)
- [ ] Spring Boot setup, PostgreSQL schema, JPA entities
- [ ] Auth (JWT) — register/login
- [ ] REST API: trenerzy, drużyny, Pokémony, movesety
- [ ] WebSocket: matchmaking (Redis queue), BattleSession
- [ ] WebSocket handler: akcje → resolve → broadcast
- [ ] Historia walk + replay storage

### Faza 3: Frontend MVP (tydzień 3–4)
- [ ] Team builder
- [ ] Matchmaking screen
- [ ] Battle screen: HP bary, log eventów, przyciski ruchów
- [ ] Replay viewer
- [ ] Leaderboard

### Faza 4: Deploy + polish (tydzień 4–5)
- [ ] Docker Compose (spring + postgres + redis + react)
- [ ] Deploy na `octo`, subdomena, TLS
- [ ] Seed data: 20 Pokémonów + 30 ruchów
- [ ] README z architekturą i live linkiem
- [ ] Dodać do CV na górę sekcji Projects

---

## Co pokazuje rekruterowi

- **WebSocket** — real-time komunikacja, nie tylko REST
- **State machine** — nietrywialny problem domenowy
- **Concurrent session management** — wiele walk jednocześnie, Redis jako shared state
- **JWT auth** — standardowy pattern produkcyjny
- **Full stack deploy** — Docker Compose na własnym serwerze, live demo
- **Testowalna logika domenowa** — silnik izolowany od frameworka, unit testowany
- **Narracyjne połączenie** — "analizowałem dane Pokémonów, potem zbudowałem silnik walk"
