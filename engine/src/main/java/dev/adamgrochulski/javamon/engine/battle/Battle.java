package dev.adamgrochulski.javamon.engine.battle;

import dev.adamgrochulski.javamon.engine.damage.TypeChart;
import dev.adamgrochulski.javamon.engine.model.Terrain;
import dev.adamgrochulski.javamon.engine.model.Weather;
import dev.adamgrochulski.javamon.engine.rng.Rng;

import java.util.List;
import java.util.stream.Stream;

public class Battle {
    private final BattleSide side1;     // P1
    private final BattleSide side2;     // P2
    private final Rng rng;
    private final TypeChart chart;
    private int turn;

    private Weather weather = Weather.NONE;
    private int weatherTurns;   // tury pozostałe; 0 przy NONE

    private Terrain terrain = Terrain.NONE;
    private int terrainTurns;   // tury pozostałe; 0 przy NONE

    public Battle(BattleSide side1, BattleSide side2, Rng rng, TypeChart chart) {
        if(side1 == null) throw new IllegalArgumentException("side1 jest wymagany");
        if(side2 == null) throw new IllegalArgumentException("side2 jest wymagany");
        if(rng == null) throw new IllegalArgumentException("rng jest wymagany");
        if(chart == null) throw new IllegalArgumentException("chart jest wymagany");

        this.side1 = side1;
        this.side2 = side2;
        this.rng = rng;
        this.chart = chart;

        turn = 1;       // Pierwsza tura
    }

    public BattleSide side(Player p) {
        return p == Player.P1 ? side1 : side2;
    }

    public Rng getRng() { return rng; }
    public TypeChart getChart() { return chart; }
    public int getTurn() { return turn; }

    public void nextTurn() { turn++; }
    public boolean isOver() { return side1.isDefeated() || side2.isDefeated(); }

    public Weather getWeather() { return weather; }

    /** Ustawia pogodę na {@code turns} tur (NONE = brak; wtedy licznik 0). */
    public void setWeather(Weather weather, int turns) {
        this.weather = weather;
        this.weatherTurns = weather == Weather.NONE ? 0 : Math.max(1, turns);
    }

    /** Odlicza turę pogody. Zwraca true, jeśli pogoda właśnie wygasła (wróciła do NONE). */
    public boolean tickWeather() {
        if (weather == Weather.NONE) {
            return false;
        }
        weatherTurns--;
        if (weatherTurns <= 0) {
            weather = Weather.NONE;
            weatherTurns = 0;
            return true;
        }
        return false;
    }

    public Terrain getTerrain() { return terrain; }

    /** Ustawia teren na {@code turns} tur (NONE = brak; wtedy licznik 0). */
    public void setTerrain(Terrain terrain, int turns) {
        this.terrain = terrain;
        this.terrainTurns = terrain == Terrain.NONE ? 0 : Math.max(1, turns);
    }

    /** Odlicza turę terenu. Zwraca true, jeśli teren właśnie wygasł. */
    public boolean tickTerrain() {
        if (terrain == Terrain.NONE) {
            return false;
        }
        terrainTurns--;
        if (terrainTurns <= 0) {
            terrain = Terrain.NONE;
            terrainTurns = 0;
            return true;
        }
        return false;
    }

    public Player winner() {
        boolean p1Dead = side1.isDefeated();
        boolean p2Dead = side2.isDefeated();

        if(p1Dead && p2Dead) return null;
        if(p2Dead) return Player.P1;
        if(p1Dead) return Player.P2;
        return null;
    }

    public boolean needsReplacement(Player p) {
        BattleSide side = side(p);
        return side.active().isFainted() && !side.isDefeated();
    }

    public List<Player> awaitingReplacement() {
        return Stream.of(Player.P1, Player.P2)
                .filter(this::needsReplacement)
                .toList();
    }
}
