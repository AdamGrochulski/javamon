<div align="center">

  <h1>Javamon</h1>
  <p>A browser-based Pokémon battle simulator, built from scratch.</p>

  <img src="https://img.shields.io/badge/status-in%20development-E6482E?style=for-the-badge&labelColor=22223B" alt="status" />

  <br/><br/>

  <img src="https://img.shields.io/badge/Java-21-E6482E?style=flat-square&logo=openjdk&logoColor=white&labelColor=22223B" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3-6DB33F?style=flat-square&logo=springboot&logoColor=white&labelColor=22223B" alt="Spring Boot 3" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white&labelColor=22223B" alt="PostgreSQL 16" />
  <img src="https://img.shields.io/badge/Redis-7-FF4438?style=flat-square&logo=redis&logoColor=white&labelColor=22223B" alt="Redis 7" />
  <img src="https://img.shields.io/badge/React-planned-61DAFB?style=flat-square&logo=react&logoColor=black&labelColor=22223B" alt="React" />
  <img src="https://img.shields.io/badge/tests-255-E6482E?style=flat-square&labelColor=22223B" alt="tests" />

</div>

---

Pokémon Showdown is the reference, not the target. The point of this project is
not to clone it - it is to build every layer myself and understand why each one
looks the way it does. Battle engine, wire protocol, matchmaking, persistence.
No STOMP, no message broker, no engine pulled off a shelf.

It is a learning project that happens to be a real system: two players connect
over a WebSocket, an authoritative server resolves turns, and the client is
never trusted with anything.

---

## The battle engine

Pure Java. Zero framework dependencies, **165 unit tests**, runs without a
Spring context. Everything the modern simplified ruleset needs:

| | Area | What is in |
|--|------|------------|
| ⚔️ | **Damage** | STAB, criticals, random roll, type chart, weather, terrain, screens |
| 🧪 | **Status** | BRN, PSN, PAR, SLP, FRZ and escalating TOX, with stat mods and move blocking |
| 📈 | **Stat stages** | -6 to +6, integer fractions, applied where the stat is read |
| 💥 | **Move effects** | Status chance, stat changes, heal, recoil, drain, flinch, confusion, multi-hit, two-turn moves, OHKO, partial trap, protect, leech seed |
| 🌧️ | **Field** | Rain, sun, sandstorm, snow, four terrains, Reflect / Light Screen / Aurora Veil |
| 🪨 | **Hazards** | Stealth Rock, Spikes, Toxic Spikes, Sticky Web, with layer counting |
| 🔄 | **Turn flow** | Priority, speed ties, forced switches after a faint, U-turn and Volt Switch pivots |

Every turn comes out as an ordered list of `BattleEvent` records. That list is
the only thing the frontend will ever render from, and the same list is the
replay.

### Deterministic by construction

All randomness - damage roll, critical, accuracy, speed tie, sleep length -
goes through an injected `Rng` interface, and the call order is fixed and
deliberate: accuracy, then crit, then roll. Tests hand the engine a fake RNG
and compute the expected number by hand. No flaky tests, and replays are exact.

```bash
./mvnw -pl engine exec:java    # seeded self-play demo, prints the whole battle
```

## The server

Spring Boot sits on top of the engine and never leaks into it. The dependency
runs one way: `app` → `engine`.

- **Auth** - stateless JWT, BCrypt, register / login, plus guest accounts so
  anyone can try the demo without signing up.
- **REST** - Pokédex browsing and team CRUD, with moveset legality validated
  server-side against the learnset.
- **WebSocket** - a hand-written JSON protocol on a raw `WebSocketHandler`.
  Token in the first frame, per-battle sequence numbers for resumption after a
  dropped connection, per-recipient event filtering.

## Under the hood

The decisions that shaped the code, and why:

**The server is authoritative, always.** The client sends intent - move,
switch, forfeit - and nothing else. Every action is re-validated from scratch:
does the battle exist, is this trainer in it, is it still that turn, does the
move belong to the moveset, is there PP left, is the switch target alive. The
legal-action list the server sends is a rendering hint, not authorization.

**The opponent's team is hidden information.** Hiding it in the UI is
worthless when the Network tab exists, so events are filtered per recipient
before they are serialized, and opponent HP goes over the wire as a
percentage - an exact number would leak their stats.

**The engine knows nothing about JSON, HTTP or Spring.** It is a separate Maven
module whose classpath has no framework on it, so the compiler enforces the
boundary rather than a code review.

**Active battles live in Redis, results and replays in PostgreSQL.** Schema
belongs to Flyway; Hibernate only validates it at startup. A replay is the
stored event list, written once, read whole.

**Data is separated from code.** Moves, species and the type chart are JSON
loaded at runtime. Adding a move is a line in a file, not a recompile.

### Data

- **850 moves** generated from Pokémon Showdown. 708 fully implemented; 142
  carry a `simplified` flag - they load with their type, power and PP, but
  their bespoke mechanic (Substitute, Encore, Disable, fixed damage) is not
  modelled yet. The generator over-flags on purpose: anything unrecognised is
  marked simplified.
- **1025 species**, learnsets filtered down to moves the engine actually knows,
  so no species can ever point at a move the dex cannot resolve.

Both files come from scripts in `tools/`.

## Running it

Requires JDK 21+ and Docker.

```bash
docker compose up -d                  # Postgres + Redis
./mvnw test                           # full build, 255 tests
./mvnw install -DskipTests            # publishes javamon-engine to ~/.m2
./mvnw -pl app spring-boot:run        # backend on :8080
```

The `install` step matters after **every engine change**: `-pl app` builds only
the `app` module and resolves `javamon-engine` as a finished jar from the local
repository. Skip it and you get a `ClassNotFoundException` on the class you just
wrote. One-liner alternative: `./mvnw -pl app -am spring-boot:run`.

Changing `pom.xml` needs a `clean` first - Maven compiles incrementally off
source file timestamps and will not notice a configuration change on its own.

`spring-boot:run` activates the `dev` profile itself. **A built jar has no
default profile** and refuses to start without `SPRING_PROFILES_ACTIVE` and
`JWT_SECRET` - a misconfigured deploy should fail closed, not quietly fall back
to the secret that sits in the repository. Copy `.env.example` to `.env` for
your own environment.

```bash
curl -X POST localhost:8080/api/auth/guest
```

## Layout

```
engine/   pure Java battle engine
  model     data and state: types, stats, moves, species, BattlePokemon
  rng       injected randomness
  damage    type chart and damage calculator
  battle    actions, events, battle state, turn resolver
app/      Spring Boot: REST, WebSocket, persistence
  auth        JWT, security config, registration and login
  api         Pokédex and team endpoints
  ws          protocol frames, session registry, handler
  persistence JPA entities, repositories, Flyway migrations
tools/    data generators
```

## What's next

- [x] Battle engine with the full move and field mechanic set
- [x] Auth, Pokédex and team REST API
- [x] WebSocket protocol, handler and session registry
- [ ] Battle sessions in Redis, full turn loop over the socket
- [ ] Matchmaking queue
- [ ] Replay storage and ELO ladder
- [ ] React + TypeScript frontend: team builder, battle screen, replay viewer
- [ ] Docker Compose deploy behind TLS

---

<div align="center">
  <sub>
    Javamon is a non-commercial fan project, not affiliated with or endorsed by
    Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company.<br/>
    Pokémon and character names are trademarks of their respective owners.
  </sub>
</div>
