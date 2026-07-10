# Javamon

Przeglądarkowy symulator walk Pokémonów budowany od zera — własny silnik walk (czysty Java), własny protokół WebSocket, backend Spring Boot, frontend React.

Projekt nauki: silnik i logikę piszę sam, warstwa po warstwie. Pełna koncepcja i decyzje techniczne w [`docs/`](docs/).

## Stan

Silnik walk (Faza 1 + 1.5) — kompletny, 111 testów jednostkowych. Gotowe: typy + macierz efektywności (data-driven), staty (bazowe i przeliczone na poziom), stat stages (±6), ruchy z PP/priority i systemem efektów (`MoveEffect`), wstrzykiwany RNG (determinizm), formuła obrażeń (STAB / krytyk / random), statusy (tick BRN/PSN/TOX, mody statów BRN/PAR, blokada ruchu SLP/PAR/FRZ), efekty ruchów (status z szansą, zmiana statów, heal/recoil/drain, entry hazardy Stealth Rock, pivot U-turn/Volt Switch), `MoveDex` z `moves.json`, turn resolver (kolejność akcji, MOVE/SWITCH/FORFEIT, ticki, wynik), wymuszony switch po faincie i eventy walki pod render/replay.

Dalej: Faza 2 — Spring Boot, WebSocket, PostgreSQL/Redis owijające silnik.

## Stack

Java 21 · Maven · JUnit 5 · (dalej: Spring Boot, PostgreSQL, Redis, React/TS)

## Build

```
mvn test
```

Wymaga JDK 21+.

## Demo

`BattlePokemon` gra sam ze sobą i wypisuje przebieg walki na konsolę — klasa `dev.adamgrochulski.javamon.engine.demo.BattleDemo` (`main`). Deterministyczne (seed RNG), pokazuje obrażenia, efektywność typów, krytyki i eskalację statusu TOX.

```
mvn -pl engine exec:java
```

## Struktura

- `engine/` — silnik walk, czysty Java, zero zależności od frameworka
  - `model` — dane i stan (typy, staty, ruchy, BattlePokemon)
  - `rng` — wstrzykiwana losowość (determinizm)
  - `damage` — macierz typów + kalkulator obrażeń
  - `battle` — akcje, eventy, stan walki, turn resolver
- `docs/` — koncept i dziennik decyzji
