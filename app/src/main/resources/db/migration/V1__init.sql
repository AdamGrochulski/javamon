CREATE TABLE trainers (
    id              uuid            PRIMARY KEY,
    username        varchar(32)     NOT NULL,
    password_hash   varchar(72),
    guest           boolean         NOT NULL DEFAULT false,
    rating          integer         NOT NULL DEFAULT 1000,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    deleted_at      timestamptz,

    CONSTRAINT trainers_password_required
        CHECK (guest OR password_hash IS NOT NULL)
);

CREATE UNIQUE INDEX trainers_username_key ON trainers (lower(username));

CREATE TABLE teams (
    id              uuid            PRIMARY KEY,
    trainer_id      uuid            NOT NULL REFERENCES trainers (id) ON DELETE CASCADE,
    name            varchar(64)     NOT NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT teams_name_unique_per_trainer UNIQUE (trainer_id, name)
);

CREATE TABLE team_slots (
    id              bigint          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    team_id         uuid            NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    slot_index      integer         NOT NULL,
    species_id      varchar(32)     NOT NULL,
    level           integer         NOT NULL DEFAULT 50,
    move1           varchar(32)     NOT NULL,
    move2           varchar(32),
    move3           varchar(32),
    move4           varchar(32),

    CONSTRAINT team_slots_index_unique UNIQUE (team_id, slot_index),
    CONSTRAINT team_slots_index_range CHECK ( slot_index BETWEEN 0 AND 5),
    CONSTRAINT team_slots_level_range CHECK ( level BETWEEN 1 AND 100)
);

CREATE TABLE battles (
    id                      uuid            PRIMARY KEY,
    player1_id              uuid            NOT NULL REFERENCES trainers (id),
    player2_id              uuid            NOT NULL REFERENCES trainers (id),
    player1_name            varchar(32)     NOT NULL,
    player2_name            varchar(32)     NOT NULL,
    player1_rating_before   integer         NOT NULL,
    player2_rating_before   integer         NOT NULL,
    player1_rating_after    integer         NOT NULL,
    player2_rating_after    integer         NOT NULL,
    winner_id               uuid            REFERENCES trainers (id),
    result                  varchar(16)     NOT NULL,
    turns                   integer         NOT NULL,
    started_at              timestamptz     NOT NULL,
    finished_at             timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT battles_distinct_players CHECK (player1_id <> player2_id),
    CONSTRAINT battles_result_values CHECK (result IN ('KO', 'FORFEIT', 'TIMEOUT'))
);

CREATE INDEX battles_player1_idx ON battles (player1_id, finished_at DESC);
CREATE INDEX battles_player2_idx ON battles (player2_id, finished_at DESC);

CREATE TABLE battle_replays (
    battle_id   uuid    PRIMARY KEY REFERENCES battles (id) ON DELETE CASCADE,
    events      jsonb   NOT NULL
);

CREATE INDEX trainers_leaderboard_idx
    ON trainers (rating DESC)
    WHERE deleted_at IS NULL AND guest = false;