package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.model.*;
import dev.adamgrochulski.javamon.engine.rng.*;
import dev.adamgrochulski.javamon.engine.damage.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.adamgrochulski.javamon.engine.battle.Player.P1;
import static dev.adamgrochulski.javamon.engine.battle.Player.P2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnResolverWeatherTest {

    // ROLL_100: brak krytyka (chance(6)=false), random = max roll. Deterministyczne.
    private static final Rng ROLL_100 = (min, max) -> 100;

    private static final Move WATER_GUN = new Move("Water Gun", Type.WATER, MoveCategory.SPECIAL, 100, 100, 25, 0);
    private static final Move RAIN_DANCE = new Move("Rain Dance", Type.WATER, MoveCategory.STATUS, 0, 100, 5, 0,
            List.of(new MoveEffect.SetWeather(Weather.RAIN)));
    private static final Move TACKLE = new Move("Tackle", Type.NORMAL, MoveCategory.PHYSICAL, 40, 100, 35, 0);

    // Atakujący typu NORMAL (bez STAB na Water), by wyizolować modyfikator pogody.
    private static BattlePokemon poke(Move move, Type type) {
        return new BattlePokemon("P", new Stats(100, 100, 100, 100, 100, 100), type, null, 50, List.of(move));
    }

    private static Battle battle(BattlePokemon p1, BattlePokemon p2) {
        return new Battle(new BattleSide(List.of(p1)), new BattleSide(List.of(p2)), ROLL_100, new TypeChart());
    }

    @Test
    void rainBoostsWaterSunWeakensIt() {
        BattlePokemon atk = poke(WATER_GUN, Type.NORMAL);
        BattlePokemon def = poke(TACKLE, Type.NORMAL);
        TypeChart chart = new TypeChart();

        int none = DamageCalculator.calculate(atk, def, WATER_GUN, chart, ROLL_100, Weather.NONE).damage();
        int rain = DamageCalculator.calculate(atk, def, WATER_GUN, chart, ROLL_100, Weather.RAIN).damage();
        int sun = DamageCalculator.calculate(atk, def, WATER_GUN, chart, ROLL_100, Weather.SUN).damage();

        assertTrue(rain > none, "deszcz wzmacnia Water");
        assertTrue(sun < none, "słońce tłumi Water");
    }

    @Test
    void moveSetsWeather() {
        Battle b = battle(poke(RAIN_DANCE, Type.WATER), poke(TACKLE, Type.NORMAL));
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.executeMove(b, P1, new MoveAction(0), events);

        assertEquals(Weather.RAIN, b.getWeather());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.WeatherStarted ws && ws.weather() == Weather.RAIN));
    }

    @Test
    void sandstormChipsNonImmuneOnly() {
        BattlePokemon normal = poke(TACKLE, Type.NORMAL);
        BattlePokemon rock = poke(TACKLE, Type.ROCK);
        Battle b = battle(normal, rock);
        b.setWeather(Weather.SANDSTORM, 5);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.weatherEndOfTurn(b, events);

        assertEquals(normal.getMaxHp() - normal.getMaxHp() / 16, normal.getCurrentHp());
        assertEquals(rock.getMaxHp(), rock.getCurrentHp()); // Rock niekryty przez piasek
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.WeatherHurt wh && wh.who().player() == P1));
    }

    @Test
    void weatherExpiresAfterDuration() {
        Battle b = battle(poke(TACKLE, Type.NORMAL), poke(TACKLE, Type.NORMAL));
        b.setWeather(Weather.RAIN, 1);
        List<BattleEvent> events = new ArrayList<>();

        TurnResolver.weatherEndOfTurn(b, events);

        assertEquals(Weather.NONE, b.getWeather());
        assertTrue(events.stream().anyMatch(e -> e instanceof BattleEvent.WeatherEnded));
    }
}
