# Decyzje

Rzeczy, których nie widać z kodu. Najnowsze na górze.

## 2026-07-26 — Protokół WebSocket

Pełny kontrakt: [`protocol.md`](protocol.md).

- **Token JWT w pierwszej ramce `AUTH`, nie w query paramie.** Przeglądarkowe `new WebSocket(url)` nie pozwala ustawić nagłówka `Authorization`, więc token musi pójść inaczej. Query param `?token=` odpada: URL trafia do logów Nginxa, logów proxy i historii przeglądarki, czyli w miejsca poza kontrolą aplikacji. Ceną jest połączenie istniejące przed uwierzytelnieniem — ograniczone timeoutem 10 s i zakazem wysyłania czegokolwiek poza `AUTH`.
- **Jedno konto = jedna sesja.** Drugie połączenie rozłącza pierwsze kodem 4409. Dwie karty przeglądarki w tej samej walce czynią kolejność akcji nieokreśloną, a serwer autorytatywny nie może mieć niedookreślonej kolejności.
- **Akcje niosą numer tury.** Bez tego akcja spóźniona przez lag zostałaby policzona w następnej turze, czyli w zupełnie innej sytuacji na polu. Przy okazji rozwiązuje to duplikaty: druga akcja na tę samą turę jest odrzucana, więc klient może retransmitować bez ryzyka podwójnego wykonania.
- **`REQUEST_ACTION` niesie listę legalnych akcji, ale to podpowiedź do renderowania, nie autoryzacja.** Gdyby klient liczył legalność sam, ta logika istniałaby w Javie i TypeScripcie naraz i musiałaby się zgadzać co do joty. Serwer i tak waliduje każdą akcję od zera.
- **Drużyna przeciwnika jest informacją ukrytą — eventy filtrowane per odbiorca.** Ukrywanie w UI nic nie daje, bo wystarczy zakładka Network. Konsekwencja: nie ma broadcastu jednym stringiem do obu graczy. HP przeciwnika w procentach, bo dokładna wartość zdradza staty.
- **`seq` w kopercie ramek serwer→klient.** Numer porządkowy w obrębie walki, wyłącznie po to, żeby po zerwaniu połączenia dało się dosłać brakujące ramki (`RESUME` z `lastSeq`). Timer tury nie zatrzymuje się na czas rozłączenia — inaczej wystarczyłoby wyciągnąć wtyczkę, żeby zawiesić przegrywaną walkę.
- **`ERROR` nie zamyka połączenia, zamknięcie jest osobną decyzją.** Nieznana ramka może pochodzić od nowszego klienta; zerwanie połączenia z tego powodu byłoby nieproporcjonalne. Zamykamy tylko przy problemie z tożsamością (4401, 4408, 4409).

## 2026-07-22 — Auth: JWT, konto gościa, fail-closed konfiguracja

Szczegóły i backlog: [`security.md`](security.md).

- **Stateless JWT, token w nagłówku `Authorization`.** Zero sesji po stronie serwera (`SessionCreationPolicy.STATELESS`) — warunek uruchomienia więcej niż jednej instancji za proxy bez współdzielenia sesji. `csrf().disable()` jest bezpieczne **wyłącznie** dlatego, że token nie jest poświadczeniem ambientowym; przeniesienie go do ciasteczka wymaga włączenia CSRF z powrotem.
- **Payload: `sub`, `name`, `guest` — nic więcej.** Payload jest podpisany, ale jawny. Nie wchodzi tam nic poufnego ani nic zmiennego (rating jest w bazie — token to zamrożona kopia sprzed maks. 24 h). `name` w tokenie oszczędza zapytanie do bazy przy każdym żądaniu.
- **TTL różne dla gościa (2 h) i konta (24 h), ale filtr o tym nie wie.** Czas życia siedzi w `exp` wewnątrz tokenu i sprawdza go biblioteka przy parsowaniu. Claim `guest` służy do autoryzacji (co wolno), nie do wygasania.
- **Filtr `JwtAuthenticationFilter` nie jest beanem.** Boot rejestruje każdy bean typu `Filter` dodatkowo w łańcuchu serwletowym — filtr wpięty jednocześnie przez `addFilterBefore` wykonywałby się dwa razy, także poza Spring Security. Tworzy go `SecurityConfig` zwykłym `new`.
- **Filtr nigdy nie odrzuca żądania sam.** Zły token = pusty `SecurityContext`; odmowę wystawia `AuthorizationFilter` na końcu łańcucha. Jedno miejsce decydujące o dostępie zamiast dwóch do utrzymania w zgodzie.
- **`DispatcherType.ERROR` na whiteliście.** Od Spring Security 6 autoryzacja obejmuje wszystkie typy dyspozycji, więc wewnętrzne przekierowanie na `/error` wpadało w `anyRequest().authenticated()` i zamieniało każde 400/404/409 na 401. `permitAll` dotyczy tu tylko przekierowań wewnątrz serwera.
- **Brak profilu domyślnego — awaria konfiguracji ma zamykać, nie otwierać.** `application.yml` nie ustawia `spring.profiles.active`; profil `dev` włącza konfiguracja `spring-boot-maven-plugin`, więc obowiązuje wyłącznie przy `mvn spring-boot:run`. Gdyby `dev` był domyślny, deploy bez zmiennej środowiskowej wziąłby sekret JWT z `application-dev.yml` — pliku w repozytorium.
- **Ochrona przed enumeracją kont przy logowaniu.** Wspólny komunikat dla „nie ma konta" i „złe hasło" oraz porównanie z `DUMMY_HASH`, gdy konta nie ma — żeby czas odpowiedzi nie zdradzał, które nicki istnieją. Przy rejestracji jest to nie do uniknięcia i zostaje zaakceptowane.
- **Zgodność sprawdzeń: kontrola przed zapisem to uprzejmość, ograniczenie w bazie to gwarancja.** `existsByUsernameIgnoreCase` obsługuje normalny przypadek, `saveAndFlush` w `try` łapie wyścig dwóch równoległych rejestracji i zamienia go na 409 zamiast 500.

## 2026-07-21 — Persystencja: Flyway + JPA

- **Schemat należy do Flywaya, Hibernate go tylko weryfikuje** (`ddl-auto: validate`). Generowanie schematu z encji jest wygodne i nie nadaje się do produkcji — rozjazd encji ze schematem ma wywalać aplikację przy starcie, nie po cichu migrować bazę.
- **UUID nadawany przez aplikację, nie bazę.** Id walki musi istnieć w Redisie w chwili utworzenia sesji, na długo przed powstaniem wiersza w Postgresie. Przy `bigserial` trzeba by trzymać dwa identyfikatory albo wstawiać pusty wiersz na starcie. Dodatkowo sekwencyjne id w publicznym URL-u replaya pozwalałoby enumerować cudze walki.
- **Encje z nadanym id implementują `Persistable`.** Spring Data wybiera `persist` albo `merge` po tym, czy id jest `null`; przy id nadawanym samodzielnie zawsze wyszedłby `merge`, czyli zbędny SELECT przed każdym INSERT-em.
- **`battles` trzyma snapshot nazw i ratingów z chwili walki.** Rekord historyczny nie może się zmieniać, gdy zmienia się teraźniejszość — zmiana nicka nie przepisuje dawnych walk, a bez `rating_before`/`after` nie da się narysować wykresu ELO.
- **Soft delete trenerów (`deleted_at`).** Chcemy jednocześnie móc „usunąć konto" i zachować czytelne walki. Konta gościa z rozegranymi walkami i tak nie dają się skasować bez zerwania referencji z `battles`.
- **Eventy jako jeden dokument `jsonb` w osobnej tabeli `battle_replays`.** `BattleEvent` ma 38 wariantów o różnych polach — relacyjnie byłaby to szeroka tabela z samymi NULL-ami albo EAV. Replay czyta się zawsze w całości i zapisuje raz, po `BattleEnd`. Osobna tabela, żeby listowanie historii nie ciągnęło bloba: `@Basic(fetch = LAZY)` na dużej kolumnie **nie działa** bez bytecode enhancement Hibernate'a.
- **`team_slots` z czterema kolumnami `move1..move4`, nie osobną tabelą.** Arność stała i mała, kolejność znacząca (protokół WS adresuje ruch indeksem), zero joinów. Normalizacja nie kupiłaby tu nic, a wymusiłaby kolumnę `position` i `ORDER BY` w każdym zapytaniu.
- **`species_id` to slug z `pokedex.json`, bez klucza obcego.** Dex żyje w jarze silnika, nie w bazie. Slug (`charizard`) zamiast numeru narodowego: `num` nie jest unikalny między formami, a nazwa niesie apostrofy i unicode (`Farfetch'd`, `Nidoran♀`). Integralności pilnuje serwis przy zapisie drużyny.

## 2026-07-20 — Pokédex w silniku

- **Generator ze Showdown (`tools/gen_pokedex.py`), analogicznie do `gen_moves.py`.** Learnsety filtrowane do ruchów obecnych w naszym `moves.json` — dzięki temu żaden gatunek nie wskazuje na ruch, którego `MoveDex` nie zna, i walidacja movesetu nie musi tego sprawdzać drugi raz. 1025 gatunków, średnio 76 ruchów w learnsecie.
- **Pomijane: formy alternatywne** (mega, regionalne, Gmax) — bez itemów i abilities mega i tak nie ma jak zadziałać. Pomijane też `isNonstandard` i wpisy techniczne (`num <= 0`).
- **`Species.learnset` jako `Set`, nie `List`.** Jedyne pytanie do learnsetu to „czy ten ruch jest legalny" — `contains` w O(1) zamiast skanu 375 pozycji przy każdym zapisie drużyny. Kolejność nieistotna.
- **`PokemonDex` bez pośredniego DTO** (inaczej niż `MoveDex`). Tam DTO jest konieczne przez dyskryminator `kind` w efektach; tutaj kształt JSON-a odpowiada rekordowi 1:1, więc Jackson czyta `Species[]` wprost.
- **`validateLearnsets(MoveDex)` poza konstruktorem.** Dex gatunków nie musi zależeć od dexu ruchów, żeby się załadować. Niezmiennika pilnuje test, produkcja płaci zero.

## 2026-07-19 — Moduł `app`: Spring Boot 3 w multi-module

- **`spring-boot-dependencies` importowany jako BOM, parentem zostaje `javamon-parent`.** Silnik dalej nie ma Springa na classpathie — BOM zarządza wyłącznie wersjami. Cena: `spring-boot-maven-plugin` wymaga jawnej wersji.
- **`mvnw` przypięty do Mavena 3.9.9** — zamyka TODO z wpisu 2026-07-03. Apt-owy Maven 3.8.7 podstawia `maven-compiler-plugin:3.1`, który nie zna `maven.compiler.release` i kompiluje na source 1.5. Plugin też przypięty jawnie (3.13.0).
- **Wrapper w wariancie `only-script`** — bez `maven-wrapper.jar`, bo `.gitignore` ignoruje `*.jar`.
- **`docker-compose.yml` to na razie sama infrastruktura** (Postgres 16, Redis 7 z healthcheckami i nazwanymi wolumenami). Aplikacja dochodzi w Fazie 4 — do tego czasu wygodniej uruchamiać ją lokalnie z hot reloadem.

## 2026-07-09 — Domknięcie silnika: pełna baza ruchów + mechaniki

- **Pełna baza ~850 ruchów z Pokémon Showdown.** Generator `tools/gen_moves.py` konwertuje ich `moves.json` na nasz schemat. Ruchy z jeszcze niemodelowaną mechaniką dostają flagę `simplified` (ładują się z podstawą — typ/moc/PP — ale bez pełnego działania). Uczciwie nad-flagujemy: cokolwiek nierozpoznanego → simplified. Po każdej nowej mechanice regenerujemy dex i flaga schodzi z pasujących ruchów. Docelowo 708/850 w pełni obsługiwanych, 142 simplified (bespoke singletony: Substitute, Encore, Disable, Counter, fixed-damage...).
- **Mechaniki dodane (każda: `MoveEffect`/pole na `Move` + obsługa w resolverze + testy):** flinch (volatile turowy), multi-hit (`Move.MultiHit`, pętla obrażeń, rozkład gen5+ dla [2,5]), pogoda (`Weather` na `Battle`, mod Water/Fire, chip SANDSTORM), warstwowe hazardy (Spikes/Toxic Spikes/Sticky Web — `BattleSide` z licznikiem warstw), ekrany (`SideCondition` czasowe, ×0.5 obrażeń), confusion (volatile 1-4 tur, self-hit typeless), ruchy dwuturowe (`Move.TwoTurn` CHARGE/RECHARGE — resolver ignoruje akcję gdy mon zablokowany), partial trap (chip 1/8 przez 4-5 tur), protect (blok + malejący łańcuch), leech seed (drain+heal), OHKO (natychmiastowy nokaut, poraża wyższy poziom), teren (`Terrain` na `Battle`, ×1.3 pasujący typ naziemnym, GRASSY heal).
- **Pole walki (pogoda/teren) na `Battle`, nie na `BattleSide`.** Globalne dla obu stron, z licznikiem tur; efekt liczony w punkcie konsumpcji (`DamageCalculator`) i na końcu tury. `DamageCalculator.calculate` przeciążony (weather → screened → terrain), stare 5-arg wywołania i testy nietknięte.
- **Naziemność przybliżana typem (nie-Flying).** Bez itemów/abilities (Levitate, Air Balloon) — wystarcza dla hazardów kontaktowych, terenu i Toxic Spikes. Dojdzie z Fazą 2/itemami.
- **Kolejność residuali końca tury:** status → uwięzienie → leech seed → pogoda → teren → ekrany. Każdy sprawdza `isOver` przez wspólny guard po fazie.
- **Volatile'e turowe vs trwałe.** `clearTurnVolatiles` (koniec tury) kasuje tylko flinch i protect; confusion/trap/leech seed/charge/recharge trwają między turami i znikają po własnym warunku.

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
