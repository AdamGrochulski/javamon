# Javamon

Przeglądarkowy symulator walk Pokémonów budowany od zera — własny silnik walk (czysty Java), własny protokół WebSocket, backend Spring Boot, frontend React.

Projekt nauki: silnik i logikę piszę sam, warstwa po warstwie. Pełna koncepcja i decyzje techniczne w [`docs/`](docs/).

## Stan

Silnik walk (Faza 1) — kompletny, 63 testy jednostkowe. Gotowe: typy + macierz efektywności (data-driven), staty (bazowe i przeliczone na poziom), ruchy z PP i priority, wstrzykiwany RNG (determinizm), formuła obrażeń (STAB / krytyk / random), statusy z tickiem końca tury (BRN/PSN/TOX), turn resolver (kolejność akcji, MOVE/SWITCH/FORFEIT, ticki, wynik) i eventy walki pod render/replay.

Dalej: Faza 2 — Spring Boot, WebSocket, PostgreSQL/Redis owijające silnik.

## Stack

Java 21 · Maven · JUnit 5 · (dalej: Spring Boot, PostgreSQL, Redis, React/TS)

## Build

```
mvn test
```

Wymaga JDK 21+.

## Demo

`BattlePokemon` gra sam ze sobą i wypisuje przebieg walki na konsolę — klasa `dev.adamgrochulski.javamon.engine.demo.BattleDemo` (`main`), uruchamiana z IDE. Deterministyczne (seed RNG), pokazuje obrażenia, efektywność typów, krytyki i eskalację statusu TOX.

## Struktura

- `engine/` — silnik walk, czysty Java, zero zależności od frameworka
  - `model` — dane i stan (typy, staty, ruchy, BattlePokemon)
  - `rng` — wstrzykiwana losowość (determinizm)
  - `damage` — macierz typów + kalkulator obrażeń
  - `battle` — akcje, eventy, stan walki, turn resolver
- `docs/` — koncept i dziennik decyzji
