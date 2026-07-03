# Decyzje

Rzeczy, których nie widać z kodu. Najnowsze na górze.

## 2026-07-03 — Macierz typów

- **Efektywności typów w JSON** (`engine/src/main/resources/type-chart.json`), nie w kodzie. Same wyjątki (nie-1.0); default 1.0 wypełnia `TypeChart`. Dane oddzielone od logiki, edytowalne bez rekompilacji.
- **Jackson w silniku** (`jackson-databind`) do parsowania. Zwykła biblioteka, nie Spring — zasada „silnik bez Springa" trzymana (silnik dalej testowalny bez kontekstu Springa).

## 2026-07-03 — Szkielet

- **Maven, multi-module.** Moduł `engine` (czysty Java) i przyszły `app` (Spring) rozdzielone, żeby kompilator pilnował, że silnik nie ciągnie Springa — `engine/pom.xml` nie ma go na classpath.
- **`app/` dojdzie w Fazie 2.** Na razie parent buduje tylko `engine`.
- **Target Java 21** mimo nowszego JDK lokalnie — ustawione na sztywno w `pom.xml`.
- **Brak `mvnw`** — do dodania przed CI, żeby wersja Mavena była powtarzalna.
