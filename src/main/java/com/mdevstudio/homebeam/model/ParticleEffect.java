package com.mdevstudio.homebeam.model;

import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.jspecify.annotations.Nullable;

// One shape of particles from the effects section; fields a shape does not use keep their defaults.
public record ParticleEffect(
        Particle particle,
        float size,
        int count,
        double spread,
        double speed,
        int strands,
        double radius,
        double height) {

    private static final Set<Class<?>> SUPPORTED_DATA = Set.of(
            Void.class,
            Color.class,
            Particle.DustOptions.class,
            Particle.DustTransition.class);

    public static boolean supports(Particle particle) {
        return SUPPORTED_DATA.contains(particle.getDataType());
    }

    public @Nullable Object dataFor(Color color) {
        Class<?> type = particle.getDataType();
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(color, size);
        }
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(color, Color.WHITE, size);
        }
        if (type == Color.class) {
            return color;
        }
        return null;
    }
}
