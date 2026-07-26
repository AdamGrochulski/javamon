# Protokół WebSocket

Własny protokół JSON na surowym WebSockecie. **Bez STOMP, bez brokera** —
decyzja projektowa: każda warstwa budowana samodzielnie.

Endpoint: `/ws/battle`

## Koperta

Każda ramka w obie strony jest obiektem JSON z polem `type`:

```json
{ "type": "MOVE", "payload": { "battleId": "0f0a...", "turn": 4, "moveIndex": 0 } }
```

Ramki serwer→klient dodatkowo niosą `seq` — licznik rosnący o 1 w obrębie
jednej walki. Służy do wznowienia po zerwaniu połączenia (patrz: [Reconnect](#reconnect)).

```json
{ "type": "TURN_EVENTS", "seq": 17, "payload": { } }
```

Nieznany `type`, brak `type`, połamany JSON albo ramka binarna → `ERROR`
z kodem `bad_frame`. Serwer nie zamyka połączenia — klient może być nowszej
wersji i wysłać coś, czego jeszcze nie znamy.

## Uwierzytelnienie

Token JWT idzie w **pierwszej ramce**, nie w query paramie. Powód: URL
z tokenem trafia do logów Nginxa, logów proxy i historii przeglądarki.
Nagłówek `Authorization` odpada — przeglądarkowe `new WebSocket()` nie
pozwala ustawić nagłówków.

```
klient → { "type": "AUTH", "payload": { "token": "eyJ..." } }
serwer → { "type": "AUTH_OK", "payload": { "trainerId": "...", "username": "ash", "guest": false } }
```

Reguły:

- Do czasu `AUTH_OK` jedyną akceptowaną ramką jest `AUTH`. Każda inna →
  `ERROR` `unauthenticated`, bez zamknięcia.
- Brak `AUTH` w ciągu **10 s** → zamknięcie kodem `4408`.
- Zły albo wygasły token → zamknięcie kodem `4401`. Bez wyjaśnień, jaki
  dokładnie był problem.
- Drugie połączenie tego samego konta rozłącza pierwsze kodem `4409`.
  Jedno konto = jedna sesja; inaczej dwie karty przeglądarki grają w tę samą
  walkę i kolejność akcji przestaje być określona.

## Klient → serwer

| `type` | `payload` | Opis |
|---|---|---|
| `AUTH` | `token` | Musi być pierwsza. |
| `QUEUE_JOIN` | `teamId` | Wejście do kolejki matchmakingu wybraną drużyną. |
| `QUEUE_LEAVE` | — | Wyjście z kolejki. |
| `MOVE` | `battleId`, `turn`, `moveIndex` | Ruch aktywnego Pokémona. |
| `SWITCH` | `battleId`, `turn`, `benchIndex` | Zmiana Pokémona. |
| `FORFEIT` | `battleId` | Poddanie walki. |
| `RESUME` | `battleId`, `lastSeq` | Wznowienie po zerwaniu połączenia. |
| `PING` | — | Odpowiedź: `PONG`. |

### Dlaczego akcje niosą `turn`

Bez numeru tury akcja spóźniona przez lag zostałaby policzona w następnej
turze — gracz klika Thunderbolt w turze 4, pakiet dociera w turze 5 i trafia
w zupełnie inną sytuację na polu. Serwer odrzuca akcję, której `turn` nie
zgadza się z aktualnym: `ERROR` `stale_action`.

Ten sam mechanizm załatwia duplikaty: druga akcja na tę samą turę dostaje
`ERROR` `action_already_submitted`. Klient może retransmitować bez ryzyka
podwójnego wykonania.

## Serwer → klient

| `type` | `payload` |
|---|---|
| `AUTH_OK` | `trainerId`, `username`, `guest` |
| `QUEUED` | `since` |
| `BATTLE_START` | `battleId`, `you`, `opponent`, `yourTeam`, `opponentActive` |
| `REQUEST_ACTION` | `battleId`, `turn`, `kind`, `deadline`, `legal` |
| `TURN_EVENTS` | `battleId`, `turn`, `events[]` |
| `BATTLE_END` | `battleId`, `winner`, `reason`, `ratingBefore`, `ratingAfter` |
| `ERROR` | `code`, `message` |
| `PONG` | — |

### `BATTLE_START`

Drużyna przeciwnika jest informacją ukrytą. Serwer wysyła każdemu graczowi
**tylko to, co ten gracz ma prawo wiedzieć**: własny skład w całości, po
stronie przeciwnika wyłącznie aktywnego Pokémona. Kolejne odsłaniają się
przez event `SWITCH`.

Ukrywanie w UI tu nie wystarcza — wystarczy zakładka Network w devtoolsach.
Dlatego eventy są filtrowane per odbiorca, a nie broadcastowane jednym
stringiem do obu.

```json
{
  "type": "BATTLE_START",
  "seq": 1,
  "payload": {
    "battleId": "0f0a...",
    "you": "P1",
    "opponent": { "username": "gary", "rating": 1042 },
    "yourTeam": [
      {
        "teamIndex": 0,
        "speciesId": "charizard",
        "name": "Charizard",
        "level": 50,
        "maxHp": 153,
        "currentHp": 153,
        "status": "NONE",
        "types": ["FIRE", "FLYING"],
        "moves": [
          { "index": 0, "name": "Flamethrower", "type": "FIRE", "pp": 15, "maxPp": 15 }
        ]
      }
    ],
    "opponentActive": {
      "teamIndex": 0,
      "name": "Blastoise",
      "level": 50,
      "hpPercent": 100,
      "status": "NONE",
      "types": ["WATER"]
    }
  }
}
```

HP przeciwnika podawane jest **w procentach**, nie w punktach — dokładna
wartość zdradza jego staty (a przez to EV/nature, gdy dojdą).

### `REQUEST_ACTION`

```json
{
  "type": "REQUEST_ACTION",
  "seq": 2,
  "payload": {
    "battleId": "0f0a...",
    "turn": 1,
    "kind": "TURN",
    "deadline": "2026-07-26T20:14:31Z",
    "legal": {
      "moves": [
        { "index": 0, "name": "Flamethrower", "pp": 15, "usable": true },
        { "index": 1, "name": "Fly", "pp": 0, "usable": false, "reason": "Brak PP" }
      ],
      "switches": [1, 2, 4]
    }
  }
}
```

`kind`:

- `TURN` — normalna tura, dozwolone `MOVE`, `SWITCH`, `FORFEIT`.
- `REPLACEMENT` — Pokémon padł, dozwolony **wyłącznie** `SWITCH`. Odpowiada
  `TurnResolver.resolveReplacement`.

`legal` to **podpowiedź do renderowania, nie autoryzacja**. Serwer waliduje
przysłaną akcję od zera, tak jakby żadnej listy nie wysyłał — klient nie musi
być naszą aplikacją.

`deadline`: 30 s od wysłania ramki. Po jego upływie akcję wybiera serwer
(Krok 7).

### `TURN_EVENTS`

Cała tura idzie jedną ramką — lista eventów w kolejności, w jakiej zwrócił je
`TurnResolver`. Klient odtwarza je sekwencyjnie z animacją; kolejność jest
jedyną informacją o przyczynowości, więc nie wolno jej gubić ani sortować po
stronie klienta.

```json
{
  "type": "TURN_EVENTS",
  "seq": 3,
  "payload": {
    "battleId": "0f0a...",
    "turn": 1,
    "events": [
      {
        "type": "MOVE_USED",
        "user": { "player": "P1", "teamIndex": 0, "name": "Charizard" },
        "moveName": "Flamethrower"
      },
      {
        "type": "DAMAGE",
        "target": { "player": "P2", "teamIndex": 0, "name": "Blastoise" },
        "damage": 41,
        "remainingHp": 132,
        "crit": false,
        "effectiveness": 0.5
      }
    ]
  }
}
```

## Mapowanie `BattleEvent` → JSON

Nazwy pól pochodzą wprost ze składowych rekordów w
`engine/battle/BattleEvent.java`. Dyskryminator `type` dokłada warstwa
serializacji w module `app` — silnik nie wie o istnieniu JSON-a.

`ref` = `{ "player": "P1" | "P2", "teamIndex": 0..5, "name": "Charizard" }`

| `type` | Pola |
|---|---|
| `SWITCH` | `out`: ref, `in`: ref |
| `MOVE_USED` | `user`: ref, `moveName` |
| `MOVE_MISSED` | `user`: ref, `moveName` |
| `DAMAGE` | `target`: ref, `damage`, `remainingHp`, `crit`, `effectiveness` |
| `NO_EFFECT` | `target`: ref |
| `FAINT` | `who`: ref |
| `STATUS_TICK` | `who`: ref, `status`, `damage`, `remainingHp` |
| `STATUS_INFLICTED` | `target`: ref, `status` |
| `STAT_STAGE_CHANGED` | `who`: ref, `stat`, `delta`, `newStage` |
| `HEALED` | `who`: ref, `amount`, `remainingHp` |
| `RECOIL_DAMAGE` | `who`: ref, `damage`, `remainingHp` |
| `HAZARD_SET` | `side`, `condition` |
| `HAZARD_HURT` | `who`: ref, `condition`, `damage`, `remainingHp` |
| `IMMOBILIZED` | `who`: ref, `status` |
| `FLINCHED` | `who`: ref |
| `CONFUSION_STARTED` | `who`: ref |
| `CONFUSION_HIT` | `who`: ref, `damage`, `remainingHp` |
| `CONFUSION_ENDED` | `who`: ref |
| `CHARGING` | `who`: ref, `moveName` |
| `RECHARGING` | `who`: ref |
| `TRAPPED` | `who`: ref |
| `TRAP_HURT` | `who`: ref, `damage`, `remainingHp` |
| `TRAP_ENDED` | `who`: ref |
| `PROTECT_STARTED` | `who`: ref |
| `PROTECTED` | `who`: ref |
| `MOVE_FAILED` | `who`: ref, `moveName` |
| `ONE_HIT_KO` | `target`: ref |
| `SEEDED` | `who`: ref |
| `LEECH_SEED_DRAIN` | `from`: ref, `to`: ref, `amount` |
| `WEATHER_STARTED` | `weather` |
| `WEATHER_HURT` | `who`: ref, `weather`, `damage`, `remainingHp` |
| `WEATHER_ENDED` | `weather` |
| `SCREEN_SET` | `side`, `condition` |
| `SCREEN_FADED` | `side`, `condition` |
| `TERRAIN_STARTED` | `terrain` |
| `TERRAIN_ENDED` | `terrain` |
| `FORFEIT` | `who` |
| `BATTLE_END` | `winner` (`null` = remis — obie strony padły) |

Wartości enumów idą jako stringi dokładnie takie, jak w silniku:

| Enum | Wartości |
|---|---|
| `StatusCondition` | `NONE`, `BRN`, `PSN`, `TOX`, `PAR`, `SLP`, `FRZ` |
| `Weather` | `NONE`, `RAIN`, `SUN`, `SANDSTORM`, `SNOW` |
| `Terrain` | `NONE`, `ELECTRIC`, `GRASSY`, `MISTY`, `PSYCHIC` |
| `Stat` | `ATTACK`, `DEFENSE`, `SPECIAL_ATTACK`, `SPECIAL_DEFENSE`, `SPEED` |
| `SideCondition` | `STEALTH_ROCK`, `SPIKES`, `TOXIC_SPIKES`, `STICKY_WEB`, `REFLECT`, `LIGHT_SCREEN`, `AURORA_VEIL` |
| `Player` | `P1`, `P2` |

**`BATTLE_END.winner` bywa `null` i to pole musi zostać w JSON-ie.** Brak pola
znaczy „nie wiem", `null` znaczy „remis" — to różne rzeczy. Dlatego
serializacja eventów nie używa `JsonInclude.NON_NULL`.

## Błędy

```json
{ "type": "ERROR", "payload": { "code": "illegal_action", "message": "Ten ruch nie ma PP" } }
```

Ten sam kształt co błędy REST (`error` → tu `code`), żeby front miał jedną
ścieżkę obsługi.

| `code` | Znaczenie |
|---|---|
| `bad_frame` | Nie-JSON, brak `type`, nieznany `type`, ramka binarna |
| `unauthenticated` | Ramka inna niż `AUTH` przed uwierzytelnieniem |
| `illegal_action` | Ruch spoza movesetu, PP = 0, switch na padniętego lub aktywnego |
| `stale_action` | `turn` nie zgadza się z aktualną turą |
| `action_already_submitted` | Druga akcja na tę samą turę |
| `not_in_battle` | `battleId` nie istnieje albo nie jesteś jego uczestnikiem |
| `team_invalid` | Drużyna z `QUEUE_JOIN` nie istnieje lub nie należy do ciebie |
| `not_implemented` | Ramka poprawna i znana, ale funkcja jeszcze nie działa |
| `internal_error` | Cokolwiek innego; szczegóły wyłącznie do logu |

`not_implemented` istnieje, bo protokół jest kompletny wcześniej niż jego
implementacja: transport i uwierzytelnienie działają od Kroku 6, walki dochodzą
w Kroku 7, matchmaking w Kroku 8. Klient dostaje jednoznaczną odpowiedź zamiast
`internal_error`, który sugerowałby awarię.

`ERROR` nie zamyka połączenia. Zamknięcie to osobna decyzja, opisana niżej.

## Kody zamknięcia

Zakres 4000–4999 jest zarezerwowany dla aplikacji przez RFC 6455.

| Kod | Powód |
|---|---|
| `1000` | Normalne zamknięcie (klient wychodzi) |
| `1011` | Błąd serwera |
| `4401` | Token nieprawidłowy lub wygasły |
| `4408` | Brak `AUTH` w ciągu 10 s |
| `4409` | To konto połączyło się gdzie indziej |

## Reconnect

Stan walki żyje w Redisie, nie w sesji WebSocketu (Krok 7) — zerwane
połączenie nie kończy walki. Klient łączy się ponownie, wysyła `AUTH`, potem:

```
klient → { "type": "RESUME", "payload": { "battleId": "0f0a...", "lastSeq": 17 } }
```

Serwer dosyła ramki o `seq` większym niż `lastSeq`, w kolejności, i kończy
aktualnym `REQUEST_ACTION`. Stąd `seq` w kopercie: bez niego klient po
powrocie nie wiedziałby, czy przegapił turę.

Jeśli `lastSeq` jest starsze niż bufor serwera — pełny stan zamiast przyrostu
(`BATTLE_START` z bieżącą sytuacją).

Timer tury **nie zatrzymuje się** na czas rozłączenia. Inaczej wystarczyłoby
wyciągnąć wtyczkę, żeby zawiesić przegrywaną walkę.

## Czego tu nie ma

- **Czat** — poza zakresem MVP.
- **Obserwatorzy** — protokół jest na to gotowy (eventy i tak są filtrowane
  per odbiorca), ale nie ma ramek `SPECTATE_*`.
- **Kompresja** (`permessage-deflate`) — ramki są małe, nieopłacalne.
- **Wersjonowanie protokołu** — do rozważenia, gdy pojawi się klient, którego
  nie deployuję razem z serwerem. Na razie front i backend idą jednym
  `docker compose up`.
