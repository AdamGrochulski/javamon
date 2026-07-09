package dev.adamgrochulski.javamon.engine.model;

/**
 * Efekt uboczny ruchu — odpalany po trafieniu. Przy ruchu STATUS to jedyne działanie;
 * przy ruchu damage'owym efekty lecą PO zadaniu obrażeń (secondary effect).
 * <p>
 * Sealed: resolver obsługuje każdy wariant przez exhaustive switch, więc nowy typ
 * efektu nie przejdzie niezauważony. Kolejne warianty (StatChange, Hazard, ForceSwitch...)
 * dochodzą w następnych krokach Fazy 1.5.
 */
public sealed interface MoveEffect
        permits MoveEffect.InflictStatus, MoveEffect.StatChange,
                MoveEffect.Heal, MoveEffect.Recoil, MoveEffect.Drain,
                MoveEffect.Hazard, MoveEffect.ForceSelfSwitch, MoveEffect.Flinch,
                MoveEffect.SetWeather, MoveEffect.SetScreen, MoveEffect.Confuse,
                MoveEffect.Trap, MoveEffect.Protect, MoveEffect.LeechSeed {

    /** Kogo dotyczy efekt względem używającego ruchu. */
    enum Target { SELF, OPPONENT }

    /** Szansa 1..100 na zadziałanie. 100 = zawsze (resolver pomija rzut RNG). */
    int chance();

    /** Cel efektu. */
    Target target();

    /** Nakłada status na cel (Toxic 100% na przeciwnika, Flamethrower 10% burn itd.). */
    record InflictStatus(StatusCondition status, Target target, int chance) implements MoveEffect {
        public InflictStatus {
            if (status == null || status == StatusCondition.NONE) {
                throw new IllegalArgumentException("InflictStatus wymaga realnego statusu, było: " + status);
            }
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }

    /** Zmienia stopień statu celu o {@code stages} (Swords Dance +2 atk SELF, Growl -1 atk OPPONENT). */
    record StatChange(Stat stat, int stages, Target target, int chance) implements MoveEffect {
        public StatChange {
            if (stat == null) {
                throw new IllegalArgumentException("StatChange wymaga statu");
            }
            if (stages == 0 || stages < -6 || stages > 6) {
                throw new IllegalArgumentException("stages musi być w [-6,6] i != 0, było: " + stages);
            }
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }

    /** Leczy cel o {@code percent}% jego maxHp (Recover SELF 50%). Niezależne od zadanych obrażeń. */
    record Heal(int percent, Target target, int chance) implements MoveEffect {
        public Heal {
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("percent musi być w 1..100, było: " + percent);
            }
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }

    /** Odrzut: używający obrywa {@code percent}% zadanych obrażeń (Brave Bird 33%). Zawsze SELF, 100%. */
    record Recoil(int percent) implements MoveEffect {
        public Recoil {
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("percent musi być w 1..100, było: " + percent);
            }
        }

        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }

    /** Wyssanie: używający leczy się o {@code percent}% zadanych obrażeń (Giga Drain 50%). Zawsze SELF, 100%. */
    record Drain(int percent) implements MoveEffect {
        public Drain {
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("percent musi być w 1..100, było: " + percent);
            }
        }

        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }

    /** Stawia hazard po stronie przeciwnika (Stealth Rock). Zawsze OPPONENT, 100%. */
    record Hazard(SideCondition condition) implements MoveEffect {
        public Hazard {
            if (condition == null) {
                throw new IllegalArgumentException("Hazard wymaga SideCondition");
            }
        }

        @Override public Target target() { return Target.OPPONENT; }

        @Override public int chance() { return 100; }
    }

    /**
     * Po ruchu używający wycofuje się (U-turn / Volt Switch).
     * MVP: auto-podmiana na następnego żywego z ławki — silnik nie ma jeszcze
     * kanału decyzji gracza w środku tury (dojdzie z protokołem w Fazie 2).
     * Zawsze SELF, 100%.
     */
    record ForceSelfSwitch() implements MoveEffect {
        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }

    /**
     * Miesza cel (Confuse Ray 100%, Water Pulse secondary): przez 1-4 tur cel
     * ma 33% szans uderzyć siebie zamiast ruszyć się. {@code chance} = szansa nałożenia.
     */
    record Confuse(Target target, int chance) implements MoveEffect {
        public Confuse {
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }

    /**
     * Uwięzienie celu (Wrap, Fire Spin, Whirlpool): chip 1/8 maxHp co turę przez
     * 4-5 tur i blokada wycofania. {@code chance} = szansa nałożenia (zwykle 100).
     */
    record Trap(Target target, int chance) implements MoveEffect {
        public Trap {
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }
    }

    /**
     * Cel się wzdryga (Fake Out, Air Slash): jeśli jeszcze nie ruszył w tej turze,
     * traci turę. Ustawia volatile flag na celu; resolver blokuje ruch, gdy flincher
     * jest wolniejszy. Zawsze OPPONENT — {@code chance} to szansa secondary (np. 30%).
     */
    record Flinch(int chance) implements MoveEffect {
        public Flinch {
            if (chance < 1 || chance > 100) {
                throw new IllegalArgumentException("chance musi być w 1..100, było: " + chance);
            }
        }

        @Override public Target target() { return Target.OPPONENT; }
    }

    /**
     * Obsiewa cel (Leech Seed): co turę traci on 1/8 maxHp, a obsiewający się leczy.
     * Trawiaste są odporne. Zawsze OPPONENT, 100%.
     */
    record LeechSeed() implements MoveEffect {
        @Override public Target target() { return Target.OPPONENT; }

        @Override public int chance() { return 100; }
    }

    /**
     * Chroni używającego w tej turze (Protect, Detect): blokuje wrogi ruch celujący
     * w niego. Szansa maleje przy łańcuchu kolejnych użyć. Zawsze SELF, 100%
     * (właściwe powodzenie rozstrzyga resolver na podstawie łańcucha).
     */
    record Protect() implements MoveEffect {
        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }

    /**
     * Ustawia pogodę na polu (Rain Dance, Sunny Day, Sandstorm). Efekt globalny —
     * {@code target} nieistotny (SELF umownie), zawsze 100%.
     */
    record SetWeather(Weather weather) implements MoveEffect {
        public SetWeather {
            if (weather == null || weather == Weather.NONE) {
                throw new IllegalArgumentException("SetWeather wymaga realnej pogody, było: " + weather);
            }
        }

        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }

    /**
     * Stawia ekran po stronie używającego (Reflect, Light Screen, Aurora Veil):
     * połowi obrażenia pasującej kategorii przez kilka tur. Zawsze SELF, 100%.
     */
    record SetScreen(SideCondition condition) implements MoveEffect {
        public SetScreen {
            if (condition != SideCondition.REFLECT && condition != SideCondition.LIGHT_SCREEN
                    && condition != SideCondition.AURORA_VEIL) {
                throw new IllegalArgumentException("SetScreen wymaga ekranu, było: " + condition);
            }
        }

        @Override public Target target() { return Target.SELF; }

        @Override public int chance() { return 100; }
    }
}
