package dev.adamgrochulski.javamon.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

public class TypeChart {

    // Rekord type-chart.json
    private record Entry(Type attacker, Type defender, double multiplier) {}

    private static final double DEFAULT_MULTIPLIER = 1.0;

    //Macierz: [attacker][defender] -> multiplier
    private final double[][] chart;

    public TypeChart() {
        int n = Type.values().length;
        this.chart = new double[n][n];

        // Wypełnij domyślnym multiplierem 1.0
        for(double[] row : chart) {
            Arrays.fill(row, DEFAULT_MULTIPLIER);
        }

        // Nadpisz wyjątkami wczytanymi z JSON-a
        for (Entry e : load()) {
            chart[e.attacker().ordinal()][e.defender().ordinal()] = e.multiplier();
        }
    }

    // Public API: mnożnik dla pary (atak, obrona)
    public double multiplier(Type attacker, Type defender) {
        return chart[attacker.ordinal()][defender.ordinal()];
    }

    // Wczytuje i parsuje JSON z classpath
    private static Entry[] load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = TypeChart.class.getResourceAsStream("/type-chart.json")){
            if(in == null) {
                throw new IllegalStateException("Brak type-chart.json na classhpath");
            }
            return mapper.readValue(in, Entry[].class);
        } catch(IOException ex){
            throw new UncheckedIOException("Błąd czytania type-chart.json", ex);
        }

    }

}
