# Decyzje

Rzeczy, których nie widać z kodu. Najnowsze na górze.

## 2026-07-09 — Faza 1.5: system efektów ruchów

- **`MoveEffect` jako sealed lista na ruchu.** Zamiast pojedynczego `inflictedStatus` ruch trzyma `List<MoveEffect>` (InflictStatus, StatChange, Heal, Recoil, Drain, Hazard, ForceSelfSwitch). Resolver stosuje je generycznie przez exhaustive switch — nowy typ efektu nie przejdzie niezauważony. Delegujące konstruktory `Move` zachowały stare wywołania.
- **Szansa 100% pomija rzut RNG.** Efekt gwarantowany nie konsumuje losowości → sekwencja RNG deterministycznych ruchów nietknięta, stare testy zielone. Secondary (np. 10% burn) rolluje normalnie.
- **Modyfikatory w punkcie konsumpcji.** Stat stages liczone jako ułamek na intach (jak w grach, bez floating-point) i wpięte tam, gdzie stat jest czytany: damage (`effective*`) i speed (przed cięciem PAR). Warstwy: stage → status.
- **Hazardy jako stan strony.** `SideCondition` na `BattleSide` (EnumSet), nie na monie. Obrażenia wejściowe liczone przy każdej zmianie aktywnego (switch i replacement) — jeden helper `applyEntryHazards`.
- **Pivot (U-turn) = auto-podmiana w MVP.** Silnik nie ma kanału decyzji gracza w środku tury (to protokół Fazy 2), więc `ForceSelfSwitch` bierze następnego żywego z ławki. Wybór gracza dojdzie z protokołem.
- **`MoveDex` data-driven (jak TypeChart).** `moves.json` z dyskryminatorem `kind` mapowanym ręcznie na `MoveEffect` — prościej niż polimorficzna deserializacja Jacksona. Dodanie ruchu = wiersz JSON.
- **Multi-hit odłożony.** Zmienia pętlę obliczania obrażeń, nie jest post-efektem jak reszta — osobno, później.

## 2026-07-08 — Statusy: mody statów i blokada ruchu (domknięcie MVP)

- **Modyfikatory statów w punkcie konsumpcji, nie na `BattlePokemon`.** BRN tnie atak fizyczny (w `DamageCalculator`), PAR ćwiartuje speed (w `TurnResolver.firstBySpeed`). Model trzyma surowe staty; modyfikator liczy ten, kto stat czyta. Spójnie i bez ukrytego stanu.
- **Blokada ruchu przed `useMove` — PP nietknięte.** Sen/paraliż/zamrożenie nie zużywają PP. SLP: licznik tur (`applySleep`/`sleepTurn`), traci dokładnie tyle tur ile dostał. PAR: 25% full-para. FRZ: 20% thaw/turę, rozmrożony rusza się w tej samej turze. RNG wołany tylko gdy dany status obecny → sekwencja losowości nietknięta dla zdrowych monów (stare testy zielone).
- **Długość snu rolluje wołający (resolver), nie model.** `applySleep(int)` bierze gotową liczbę tur; RNG żyje w resolverze — determinizm zachowany, model bez zależności od RNG.
- **Ruchy statusowe: `Move.inflictedStatus` (nullable).** Delegujący 7-arg konstruktor → istniejące wywołania bez zmian. STATUS po trafieniu nakłada status na cel (SLP z losową długością, reszta `applyStatus`); brak nadpisania istniejącego statusu. Nowe eventy `Immobilized`, `StatusInflicted`.

## 2026-07-04 — Silnik walki (Faza 1)

- **Eventy jako kręgosłup.** `TurnResolver.resolve` zwraca `List<BattleEvent>`; front i replay renderują wyłącznie z eventów, nie liczą nic sami. Eventy samowystarczalne (np. `Damage` niesie `remainingHp`).
- **Determinizm przez wstrzykiwany `Rng`.** Kolejność wywołań RNG jest stała i celowa: przy ruchu `accuracy → crit → random`, przy kolejności tury `speed tie`. Testy dają fake RNG i liczą wynik ręcznie.
- **`Stats` (baza z dexu) vs `BattleStats` (przeliczone na poziom).** Dwa typy, żeby nie mieszać wartości bazowych z bojowymi. `BattlePokemon` = klasa (mutable HP/status), nie record.
- **`DamageResult` zamiast gołego int.** Niesie `crit`, `effectiveness`, `noEffect()` (immunity) — pod eventy i przyszłe komunikaty.
- **Statusy: tylko tick w MVP.** Non-volatile (max jeden), TOX eskaluje. Blokada ruchu (SLP/FRZ/PAR) i modyfikacja statów (BRN/PAR) odłożone — wejdą z pełną integracją w resolverze.
- **`TurnResolver` bezstanowy (jak `DamageCalculator`).** Logika osobno od stanu (`Battle`). Kolejność akcji: SWITCH przed MOVE, dalej priority → speed → RNG tie.
- **Warstwowa walidacja.** Rekordy akcji odsiewają bzdury bez kontekstu (`index < 0`); legalność względem stanu (PP>0, cel switcha żyje) waliduje resolver/serwer. Serwer autorytatywny.
- **Skróty MVP do domknięcia później:** brak walidacji obu akcji *przed* wykonaniem (polega na guardach + serwerze) i brak wymuszonego switcha po faincie.
- **Styl komentarzy:** `/** */` dla nagłówka typu i publicznego API, `//` dla notek implementacyjnych.

## 2026-07-03 — Macierz typów

- **Efektywności typów w JSON** (`engine/src/main/resources/type-chart.json`), nie w kodzie. Same wyjątki (nie-1.0); default 1.0 wypełnia `TypeChart`. Dane oddzielone od logiki, edytowalne bez rekompilacji.
- **Jackson w silniku** (`jackson-databind`) do parsowania. Zwykła biblioteka, nie Spring — zasada „silnik bez Springa" trzymana (silnik dalej testowalny bez kontekstu Springa).

## 2026-07-03 — Szkielet

- **Maven, multi-module.** Moduł `engine` (czysty Java) i przyszły `app` (Spring) rozdzielone, żeby kompilator pilnował, że silnik nie ciągnie Springa — `engine/pom.xml` nie ma go na classpath.
- **`app/` dojdzie w Fazie 2.** Na razie parent buduje tylko `engine`.
- **Target Java 21** mimo nowszego JDK lokalnie — ustawione na sztywno w `pom.xml`.
- **Brak `mvnw`** — do dodania przed CI, żeby wersja Mavena była powtarzalna.
