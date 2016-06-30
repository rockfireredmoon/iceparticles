package org.iceparticles;

import java.util.prefs.Preferences;

import org.icelib.AbstractConfig;
import org.icescene.SceneConfig;

public class ParticleConfig extends SceneConfig {
    
    public final static String PARTICLES_EDITOR = "editParticles";
    
    // Camera move speed (build mode)
    public final static String PARTICLES_MOVE_SPEED = PARTICLES_EDITOR + "MoveSpeed";
    public final static float PARTICLES_MOVE_SPEED_DEFAULT = 100f;
    // Camera zoom speed
    public final static String PARTICLES_ZOOM_SPEED = PARTICLES_EDITOR + "ZoomSpeed";
    public final static float PARTICLES_ZOOM_SPEED_DEFAULT = 10f;
    // Camera rotate speed
    public final static String PARTICLES_ROTATE_SPEED = PARTICLES_EDITOR + "RotateSpeed";
    public final static float PARTICLES_ROTATE_SPEED_DEFAULT = 10f;
    // Debug emitter
    public final static String PARTICLES_DEBUG_EMITTER = PARTICLES_EDITOR + "DebugEmitter";
    public final static boolean PARTICLES_DEBUG_EMITTER_DEFAULT = true;
    // Debug particles
    public final static String PARTICLES_DEBUG_PARTICLES = PARTICLES_EDITOR + "DebugParticles";
    public final static boolean PARTICLES_DEBUG_PARTICLES_DEFAULT = false;
    // Debug particles
    public final static String PARTICLES_TIME_SCALE= PARTICLES_EDITOR + "TimeScale";
    public final static float PARTICLES_TIME_SCALE_DEFAULT = 1f;
    
    public static Object getDefaultValue(String key) {
        return AbstractConfig.getDefaultValue(ParticleConfig.class, key);
    }

    public static Preferences get() {
        return Preferences.userRoot().node(ParticleConstants.APPSETTINGS_NAME).node("game");
    }
}
