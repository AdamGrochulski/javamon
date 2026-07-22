# Bezpieczeństwo

Stan zabezpieczeń warstwy `app` i lista rzeczy świadomie odłożonych. Aktualizowane
razem z kodem — pozycja z backlogu znika stąd dopiero, gdy jest zrobiona i sprawdzona.

## Model zagrożeń w skrócie

Aplikacja jest publicznym demo z kontami i rankingiem. Realne ryzyka, w kolejności:

1. **Podszycie się pod innego gracza** — sfałszowany albo skradziony token.
2. **Przejęcie konta** — zgadywanie haseł, brak limitu prób.
3. **Zaśmiecenie / wyczerpanie zasobów** — nieuwierzytelnione endpointy zapisujące do bazy.
4. **Oszukiwanie w walce** — klient wysyłający nielegalne akcje.

Punkt 4 adresuje zasada „serwer autorytatywny" z `CLAUDE.md`: silnik i warstwa sesji
walidują każdą akcję względem stanu, klient wysyła wyłącznie intencje.

## Zrobione

- **Hasła: BCrypt** (strength 10, sól generowana per hash i zapisana w wyniku).
  Kolumna `password_hash` jest nullowalna tylko dla gości — pilnuje tego
  `CHECK (guest OR password_hash IS NOT NULL)` w `V1__init.sql`. Konto bez hasła
  i bez flagi gościa nie może powstać, nawet przy błędzie w kodzie rejestracji.
- **Token w nagłówku `Authorization`, nigdy w ciasteczku.** To jedyny powód, dla
  którego `csrf().disable()` jest bezpieczne: atak CSRF potrzebuje poświadczenia
  ambientowego, a nagłówka przeglądarka sama nie dokleja. **Przeniesienie tokenu
  do ciasteczka wymaga włączenia CSRF z powrotem** — to nie jest decyzja
  kosmetyczna.
- **Payload tokenu bez danych wrażliwych i bez zmiennych.** Tylko `sub` (id),
  `name` i `guest`. Payload jest jawny (Base64URL), a rating trzymamy w bazie,
  bo token to zamrożona kopia sprzed maks. 24 h.
- **Whitelist zamiast blacklisty.** `anyRequest().authenticated()` jako reguła
  domykająca — nowy endpoint jest domyślnie chroniony, dopóki ktoś świadomie
  go nie otworzy.
- **Brak enumeracji kont przy logowaniu.** Jeden komunikat na „nie ma konta"
  i „złe hasło", plus porównanie z `DUMMY_HASH`, gdy konta nie ma — żeby czas
  odpowiedzi nie zdradzał, które nicki istnieją.
- **Brak profilu domyślnego (fail-closed).** `application.yml` nie ustawia
  `spring.profiles.active`. Gdyby ustawiał `dev`, deploy bez zmiennej
  środowiskowej wziąłby sekret JWT z `application-dev.yml` — pliku leżącego
  w repozytorium. Profil `dev` włącza konfiguracja `spring-boot-maven-plugin`,
  więc dotyczy wyłącznie `mvn spring-boot:run`; zbudowany jar bez
  `JWT_SECRET` **nie wstaje** (jawny `IllegalStateException` z `JwtService`).
- **Minimalna długość sekretu** sprawdzana przy starcie (32 bajty = 256 bitów).
- **Wyścig przy rejestracji kończy się 409, nie 500.** Sprawdzenie zajętości
  nicka obsługuje normalny przypadek, a `saveAndFlush` w bloku `try` łapie
  naruszenie unikalnego indeksu, gdy dwa żądania przyjdą równolegle.
- **Wyłączona autokonfiguracja `UserDetailsServiceAutoConfiguration`.** Bez tego
  Spring tworzy użytkownika `user` z hasłem wypisywanym do logu. Był nieużywalny
  (`httpBasic` i `formLogin` wyłączone), ale nieużywane poświadczenia w kontekście
  to zbędna powierzchnia ataku.
- **`forward-headers-strategy: framework`** — za Nginx Proxy Managerem Spring
  musi widzieć prawdziwy adres klienta i `X-Forwarded-Proto`. Bez tego wszystkie
  żądania wyglądają jak HTTP z jednego adresu, co przy limitach per IP oznacza
  zablokowanie wszystkich naraz albo nikogo.
- **`DispatcherType.ERROR` na whiteliście.** Od Spring Security 6 autoryzacja
  obejmuje wszystkie typy dyspozycji, więc wewnętrzne przekierowanie na `/error`
  wpadało w `anyRequest().authenticated()` i zamieniało każde 400/404/409 na 401.
  `permitAll` dotyczy tu wyłącznie przekierowań wewnątrz serwera — żądanie z sieci
  zawsze przychodzi jako `REQUEST`.

## Backlog

| # | Rzecz | Dlaczego | Kiedy |
|---|---|---|---|
| B1 | **Limit prób logowania** — licznik w Redisie po nicku **i** po IP, z TTL | BCrypt to ~80 ms, czyli ~12 prób/s z jednego połączenia. Blokada tylko po IP obchodzi się botnetem, tylko po nicku pozwala zablokować cudze konto na złość | przed publicznym demo |
| B2 | **Limit tworzenia gości + sprzątanie** | `POST /api/auth/guest` to nieuwierzytelniony zapis do bazy; pętla `curl` rośnie tabelę bez ograniczeń. Sprzątać można tylko gości bez walk — resztę blokuje FK z `battles` | przed publicznym demo |
| B3 | **Odwoływanie tokenów** — denylist w Redisie po `jti` albo krótkie TTL + refresh | Stateless oznacza brak wylogowania: skradziony token żyje do 24 h, zmiana hasła go nie unieważnia | opcjonalne w MVP, świadomy kompromis |
| B4 | **Kontrakt błędów** — `@RestControllerAdvice` zwracający JSON z kodem, komunikatem i błędami pól | Dziś 409 i 400 mają puste ciało (Spring nie wypuszcza `message` wyjątku). Front nie ma z czego zbudować komunikatu ani wskazać złego pola | Krok 5, przed frontendem |
| B5 | **CORS** — bean `CorsConfigurationSource` z originami z konfiguracji | `.cors(withDefaults())` bez beana nie dopuszcza żadnego originu. Nie używać `"*"` z `allowCredentials` — przeglądarka to odrzuci, a przy tokenie w nagłówku gwiazdka i tak jest zbędna | Krok 5 / start Fazy 3 |
| B6 | **Testy integracyjne auth** (Testcontainers + MockMvc) | Moduł `app` nie ma ani jednego testu, mimo że zależności są w pomie. Błąd z `DispatcherType.ERROR` przeszedł niezauważony właśnie dlatego | Krok 5 |
| B7 | **Nagłówki bezpieczeństwa** (HSTS, CSP) na poziomie Nginx Proxy Managera | Dotyczy dopiero serwowanego frontendu | Faza 4 |

## Znane i zaakceptowane

- **Rejestracja zdradza, czy nick jest zajęty** (409 kontra 200). Nieuniknione —
  użytkownik musi wiedzieć, czy nick jest wolny. Logowanie tego nie zdradza.
- **Brak 2FA i resetu hasła.** Poza zakresem MVP; konto gościa obniża próg wejścia
  dla oglądających demo, więc utrata konta nie blokuje korzystania.
- **`pokedex.json` i `moves.json` w jarze, nie w bazie.** `team_slots.species_id`
  nie ma klucza obcego — integralność egzekwuje serwis przy zapisie drużyny
  (`PokemonDex.has`, `Species.canLearn`). Świadome: dex jest danymi statycznymi,
  nie stanem aplikacji.
