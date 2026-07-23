# Javamon

Przeglądarkowy symulator walk Pokémonów budowany od zera — własny silnik walk (czysty Java), własny protokół WebSocket, backend Spring Boot, frontend React.

Projekt nauki: silnik i logikę piszę sam, warstwa po warstwie. Pełna koncepcja i decyzje techniczne w [`docs/`](docs/).

## Stan

**Faza 1 + 1.5 — silnik walk: ukończone.** 165 testów jednostkowych, zero zależności od frameworka. Gotowe: typy + macierz efektywności (data-driven), staty (bazowe i przeliczone na poziom), stat stages (±6), ruchy z PP/priority i systemem efektów (`MoveEffect`), wstrzykiwany RNG (determinizm), formuła obrażeń (STAB / krytyk / random / pogoda / teren / ekrany), statusy (tick BRN/PSN/TOX, mody statów BRN/PAR, blokada ruchu SLP/PAR/FRZ), efekty ruchów (status z szansą, zmiana statów, heal/recoil/drain, flinch, confusion, multi-hit, ruchy dwuturowe charge/recharge, OHKO, partial trap, protect, leech seed), efekty pola (pogoda rain/sun/sand/snow, teren electric/grassy/misty/psychic, entry hazardy Stealth Rock/Spikes/Toxic Spikes/Sticky Web, ekrany Reflect/Light Screen/Aurora Veil), pivot U-turn/Volt Switch, turn resolver (kolejność akcji, MOVE/SWITCH/FORFEIT, ticki, wynik), wymuszony switch po faincie i eventy walki pod render/replay.

**Faza 2 — backend: w toku.** Zrobione: moduł `app` (Spring Boot 3) w multi-module, Pokédex w silniku (`Species` / `PokemonDex`), schemat bazy przez Flyway, encje JPA i repozytoria, auth JWT (register / login / konto gościa). Dalej: REST, protokół WebSocket, sesje walk w Redisie, matchmaking, replay i ranking.

### Dane

- **Ruchy:** 850 wpisów z Pokémon Showdown — 708 w pełni obsługiwanych, 142 z flagą `simplified` (ładują się z podstawą, ich unikatowa mechanika — Substitute, Encore, Disable, fixed-damage itd. — dojdzie później albo zostaje jako pojedynczy przypadek).
- **Gatunki:** 1025 wpisów, średnio 76 ruchów w learnsecie. Learnsety są przefiltrowane do ruchów obecnych w `moves.json`, więc żaden gatunek nie wskazuje na ruch, którego silnik nie zna.

Oba pliki generują skrypty z `tools/` — dane są oddzielone od kodu, dodanie ruchu czy gatunku nie wymaga rekompilacji.

## Stack

Java 21 · Maven (multi-module) · JUnit 5 · Spring Boot 3 · Spring Security (JWT) · JPA/Hibernate · Flyway · PostgreSQL 16 · Redis 7 · (dalej: React/TypeScript)

## Uruchomienie lokalne

Wymagane: JDK 21+ i Docker.

```bash
docker compose up -d                  # Postgres + Redis
./mvnw test                           # cały build, 190 testów
./mvnw install -DskipTests            # instaluje javamon-engine do ~/.m2
./mvnw -pl app spring-boot:run        # backend na :8080
```

Krok z `install` jest konieczny po **każdej zmianie w silniku**: `-pl app`
buduje tylko moduł `app`, a `javamon-engine` bierze jako gotowy jar z lokalnego
repozytorium. Bez tego dostaniesz `ClassNotFoundException` na klasie, którą
przed chwilą dodałeś. Alternatywa jednym poleceniem: `./mvnw -pl app -am spring-boot:run`.

Po zmianie w `pom.xml` potrzebny jest `clean` — Maven kompiluje przyrostowo po
datach plików źródłowych i samej zmiany konfiguracji nie zauważy.

`spring-boot:run` sam włącza profil `dev` (konfiguracja w `app/pom.xml`), który
podstawia lokalne wartości pasujące do `docker-compose.yml`. **Zbudowany jar nie
ma profilu domyślnego** — bez `SPRING_PROFILES_ACTIVE` i `JWT_SECRET` nie wstanie.
Zmienne dla własnego środowiska: skopiuj `.env.example` do `.env`.

Szybki test auth:

```bash
curl -X POST localhost:8080/api/auth/guest
```

## Demo silnika

`BattlePokemon` gra sam ze sobą i wypisuje przebieg walki na konsolę — klasa `dev.adamgrochulski.javamon.engine.demo.BattleDemo` (`main`). Deterministyczne (seed RNG), pokazuje obrażenia, efektywność typów, krytyki i eskalację statusu TOX.

```bash
./mvnw -pl engine exec:java
```

## Struktura

- `engine/` — silnik walk, czysty Java, zero zależności od frameworka
  - `model` — dane i stan (typy, staty, ruchy, gatunki, `BattlePokemon`)
  - `rng` — wstrzykiwana losowość (determinizm)
  - `damage` — macierz typów + kalkulator obrażeń
  - `battle` — akcje, eventy, stan walki, turn resolver
- `app/` — Spring Boot: REST, WebSocket, persystencja. Zależność idzie w jedną stronę: `app` → `engine`
  - `auth` — JWT, konfiguracja bezpieczeństwa, rejestracja i logowanie
  - `persistence` — encje JPA i repozytoria; migracje w `resources/db/migration`
- `tools/` — generatory danych ze źródeł Pokémon Showdown
- `docs/` — [koncepcja](docs/concept.md), [dziennik decyzji](docs/decisions.md), [bezpieczeństwo](docs/security.md)
