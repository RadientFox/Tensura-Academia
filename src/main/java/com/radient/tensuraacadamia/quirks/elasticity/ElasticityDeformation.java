package com.radient.tensuraacadamia.quirks.elasticity;

public final class ElasticityDeformation {
    public static double dent(double distance, double age, float strength) {
        if (age < 0 || age >= 24) return 0;
        double spring = age < 3 ? .35 + .65 * Math.sin(age * Math.PI / 6)
                : Math.cos((age - 3) * .36);
        double contact = Math.exp(-distance * distance / 1.3);
        double ripple = Math.exp(-Math.pow(distance - age * .15, 2) / .18) * .12;
        return strength * Math.exp(-age * .14) * (spring * contact + ripple);
    }

    public static double anchored(double displacement, double fraction) {
        return Math.clamp(displacement, -1.5, 1.5) * Math.pow(Math.max(0, 1 - fraction * fraction), .7);
    }

    private ElasticityDeformation() { }
}
