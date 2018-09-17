package org.iceparticles.app;

import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.iceparticles.ParticleConfig;
import org.icescene.scene.AbstractSceneUIAppState;
import org.iceui.actions.AppAction;
import org.iceui.actions.AppAction.Style;

import icetone.controls.lists.FloatRangeSliderModel;
import icetone.controls.lists.Slider;
import icetone.core.Orientation;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;

public class UIAppState extends AbstractSceneUIAppState {

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

		if (menuBar != null) {
			menuBar.invalidate();

			menuBar.addAction(new AppAction("Debug Emitter", evt -> {
				prefs.putBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER, evt.getSourceAction().isActive());
			}).setMenu("View").setStyle(Style.TOGGLE).setActive(prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER,
					ParticleConfig.PARTICLES_DEBUG_EMITTER_DEFAULT)));

			menuBar.addAction(new AppAction("Debug Particles", evt -> {
				prefs.putBoolean(ParticleConfig.PARTICLES_DEBUG_PARTICLES, evt.getSourceAction().isActive());
			}).setMenu("View").setStyle(Style.TOGGLE).setActive(prefs.getBoolean(
					ParticleConfig.PARTICLES_DEBUG_PARTICLES, ParticleConfig.PARTICLES_DEBUG_PARTICLES_DEFAULT)));

			menuBar.validate();
		}

		// Debug Particles
//		timeScale = new Slider<Float>(screen, Orientation.HORIZONTAL);
//		timeScale.onChanged(evt -> prefs.putFloat(ParticleConfig.PARTICLES_TIME_SCALE, evt.getNewValue()));
//		float ts = prefs.getFloat(ParticleConfig.PARTICLES_TIME_SCALE, ParticleConfig.PARTICLES_TIME_SCALE_DEFAULT);
//		timeScale.setSliderModel(new FloatRangeSliderModel(0, 5, ts));
//		timeScale.setToolTipText(String.format("Time Scale : %2.2f", ts));
//		layer.addElement(timeScale, "growx");

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
