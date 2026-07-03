# Javamon

Przeglądarkowy symulator walk Pokémonów budowany od zera — własny silnik walk (czysty Java), własny protokół WebSocket, backend Spring Boot, frontend React.

Projekt nauki: silnik i logikę piszę sam, warstwa po warstwie. Pełna koncepcja i decyzje techniczne w [`docs/`](docs/).

## Stan

Wczesna faza — silnik walk. Gotowe: typy + macierz efektywności (data-driven), staty, ruchy, wstrzykiwany RNG. Dalej: formuła obrażeń, statusy, turn resolver.

## Stack

Java 21 · Maven · JUnit 5 · (dalej: Spring Boot, PostgreSQL, Redis, React/TS)

## Build

```
mvn test
```

Wymaga JDK 21+.

## Struktura

- `engine/` — silnik walk, czysty Java, zero zależności od frameworka
- `docs/` — koncept i dziennik decyzji
