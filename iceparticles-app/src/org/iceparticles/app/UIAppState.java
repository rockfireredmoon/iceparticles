package org.iceparticles.app;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.icelib.UndoManager;
import org.iceparticles.ParticleConfig;
import org.icescene.scene.AbstractSceneUIAppState;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;

import icetone.controls.buttons.CheckBox;
import icetone.controls.lists.FloatRangeSliderModel;
import icetone.controls.lists.Slider;
import icetone.core.Element.Orientation;
import icetone.core.layout.mig.MigLayout;

public class UIAppState extends AbstractSceneUIAppState {

	private CheckBox debugEmitter;
	private CheckBox debugParticles;
	private Slider<Float> timeScale;

	public UIAppState(UndoManager undoManager, Preferences prefs) {
		super(undoManager, prefs);
		addPrefKeyPattern(ParticleConfig.PARTICLES_EDITOR + ".*");
	}

	@Override
	protected void addAfter() {

	}

	@Override
	protected void addBefore() {

		// Emitter Particles
		debugEmitter = new CheckBox(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				prefs.putBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER, toggled);
			}
		};
		debugEmitter.setIsCheckedNoCallback(prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER,
				ParticleConfig.PARTICLES_DEBUG_EMITTER_DEFAULT));
		debugEmitter.setLabelText("Emitter");
		layer.addChild(debugEmitter);

		// Debug Particles
		debugParticles = new CheckBox(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				prefs.putBoolean(ParticleConfig.PARTICLES_DEBUG_PARTICLES, toggled);
			}
		};
		debugParticles.setIsCheckedNoCallback(prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_PARTICLES,
				ParticleConfig.PARTICLES_DEBUG_PARTICLES_DEFAULT));
		debugParticles.setLabelText("Particles");
		layer.addChild(debugParticles);

		// Debug Particles
		timeScale = new Slider<Float>(screen, Orientation.HORIZONTAL, true) {

			@Override
			public void onChange(Float value) {
				prefs.putFloat(ParticleConfig.PARTICLES_TIME_SCALE, (Float) value);
			}
		};
		float ts = prefs.getFloat(ParticleConfig.PARTICLES_TIME_SCALE, ParticleConfig.PARTICLES_TIME_SCALE_DEFAULT);
		timeScale.setSliderModel(new FloatRangeSliderModel(0, 5, ts));
		timeScale.setToolTipText(String.format("Time Scale : %2.2f", ts));
		layer.addChild(timeScale, "growx");

	}

	@Override
	protected void handlePrefUpdateSceneThread(PreferenceChangeEvent evt) {
		if (evt.getKey().equals(ParticleConfig.PARTICLES_TIME_SCALE)) {
			float ts = Float.parseFloat(evt.getNewValue());
			timeScale.setToolTipText(String.format("Time Scale : %2.2f", ts));
			((FloatRangeSliderModel) timeScale.getSliderModel()).setValue(ts);
		}
	}

	@Override
	protected MigLayout createLayout() {
		return new MigLayout(screen, "fill", "[][][:200:]push[][][][]", "[]push");
	}
}
