# Decyzje

Rzeczy, których nie widać z kodu. Najnowsze na górze.

## 2026-07-03 — Szkielet

- **Maven, multi-module.** Moduł `engine` (czysty Java) i przyszły `app` (Spring) rozdzielone, żeby kompilator pilnował, że silnik nie ciągnie Springa — `engine/pom.xml` nie ma go na classpath.
- **`app/` dojdzie w Fazie 2.** Na razie parent buduje tylko `engine`.
- **Target Java 21** mimo nowszego JDK lokalnie — ustawione na sztywno w `pom.xml`.
- **Brak `mvnw`** — do dodania przed CI, żeby wersja Mavena była powtarzalna.
