package org.iceparticles;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icescene.IcesceneApp;
import org.icescene.assets.ExtendedMaterialListKey;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.ogreparticle.TimedEmitter;
import org.iceui.controls.MaterialFieldControl;

import com.jme3.material.Material;
import com.jme3.material.MaterialList;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import emitter.EmitterMesh.DirectionType;
import icemoon.iceloader.ServerAssetManager;
import icetone.controls.buttons.CheckBox;
import icetone.controls.lists.ComboBox;
import icetone.controls.lists.IntegerRangeSpinnerModel;
import icetone.controls.lists.Spinner;
import icetone.controls.scrolling.ScrollPanel;
import icetone.controls.text.Label;
import icetone.core.BaseScreen;
import icetone.core.Orientation;
import icetone.core.ToolKit;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.extras.chooser.ChooserDialog;
import icetone.extras.chooser.ChooserModel;
import icetone.extras.chooser.StringChooserModel;
import icetone.extras.controls.Vector2fControl;
import icetone.extras.controls.Vector3fControl;

public class ScriptEditPanel extends ScrollPanel {

	private final MaterialFieldControl material;
	private final Vector2fControl particleSize;
	private final Spinner<Integer> quota;
	private final CheckBox cullEach;
	private final CheckBox sorted;
	private final CheckBox localSpace;
	private final ComboBox<OGREParticleScript.BillboardOrigin> billboardOrigin;
	private final ComboBox<OGREParticleScript.BillboardRotation> billboardRotation;
	private final ComboBox<DirectionType> directionType;
	private final ComboBox<OGREParticleScript.BillboardType> billboardType;
	private final UndoManager undoManager;
	private final ParticleViewerAppState particleViewer;
	private OGREParticleScript script;
	private Vector3fControl commonDirection;
	private Vector3fControl commonUpVector;
	private Map<String, MaterialList> materialNames;

	public ScriptEditPanel(Preferences prefs, ParticleViewerAppState particleViewer, UndoManager undoManager,
			BaseScreen screen) {
		super(screen);

		setScrollContentLayout(new MigLayout(screen, "wrap 2", "[shrink 0][]", "[]"));

		this.particleViewer = particleViewer;
		this.undoManager = undoManager;

		// Quota
		addScrollableContent(new Label("Quota", screen));
		addScrollableContent(quota = new Spinner<Integer>(screen, Orientation.HORIZONTAL, false));
		quota.setInterval(5);
		quota.setSpinnerModel(new IntegerRangeSpinnerModel(0, 999999, 1, 0));
		quota.setToolTipText("Sets the maximum number of particles this system is allowed "
				+ "to contain at one time. When this limit is exhausted, the emitters "
				+ "will not be allowed to emit any more particles until some destroyed "
				+ "(e.g. through their time_to_live running out). Note that you will "
				+ "almost always want to change this, since it defaults to a very low "
				+ "value (particle pools are only ever increased in size, never " + "decreased).");

		// Material

		addScrollableContent(new Label("Material", screen));
		addScrollableContent(material = new MaterialFieldControl(screen, null, null, prefs) {
			@Override
			protected ChooserModel<String> loadResources() {
				checkMaterialNames();
				return new StringChooserModel(materialNames.keySet());
			}

			@Override
			protected void retrieveResources(ChooserDialog<String> chooser) {
				if (resources == null) {
					((IcesceneApp) ToolKit.get().getApplication()).getWorldLoaderExecutorService()
							.execute(new Runnable() {
								@Override
								public void run() {
									resources = loadResources();
									ToolKit.get().getApplication().enqueue(new Callable<Void>() {

										@Override
										public Void call() throws Exception {
											chooser.setResources(resources);
											return null;
										}
									});
								}

								@Override
								public String toString() {
									return "Loading Materials";
								}

							});
				}
			}

			protected void checkMaterialNames() {
				if (materialNames == null) {
					materialNames = getMaterialNames();
				}
			}

			@Override
			protected String getChooserPathFromValue() {
				return value;
			}

			@Override
			protected void setValueFromChooserPath(String path) {
				value = path;
			}

			@Override
			protected void onResourceChosen(String newResource) {
				ScriptEditPanel.this.undoManager.storeAndExecute(new UpdateMaterialCommand(script, newResource));
			}

			@Override
			public MaterialList getMaterialList(String path) {
				checkMaterialNames();
				return materialNames.get(path);
			}
		}, "growx");
		material.setToolTipText("Sets the name of the material which all particles in "
				+ "this system will use. All particles in a system use the same "
				+ "material, although each particle can tint this material through "
				+ "the use of it’s colour property.");

		// Particle Size
		addScrollableContent(new Label("Particle Size", screen));
		particleSize = new Vector2fControl(screen, 0.0f, 99999f, 0.1f, new Vector2f(), true);
		particleSize.onChange(evt -> ScriptEditPanel.this.undoManager
				.storeAndExecute(new UpdateParticleSizeCommand(script, evt.getNewValue().clone())));
		addScrollableContent(particleSize);
		particleSize.setToolTipText("Sets the width of particles in world coordinates. "
				+ "Note that this property is absolute when billboard_type (see below) "
				+ "is set to ’point’ or ’perpendicular_self’, but is scaled by the "
				+ "length of the direction vector when billboard_type is "
				+ "’oriented_common’, ’oriented_self’ or ’perpendicular_common’.");

		// Common Direction
		addScrollableContent(new Label("Common Direction", screen));
		addScrollableContent(commonDirection = new Vector3fControl(screen, -1f, 1f, 0.1f, new Vector3f(), false));
		commonDirection.onChange(evt -> ScriptEditPanel.this.undoManager
				.storeAndExecute(new UpdateCommonDirectionCommand(script, evt.getNewValue().clone())));
		commonDirection.setToolTipText(
				"Only required if billboard_type is set to oriented_common or perpendicular_common, this vector is the common direction vector used to orient all particles in the system.");

		// Common Up
		addScrollableContent(new Label("Common Up", screen));
		addScrollableContent(commonUpVector = new Vector3fControl(screen, -1f, 1f, 0.1f, new Vector3f(), false));
		commonUpVector.onChange(evt -> ScriptEditPanel.this.undoManager
				.storeAndExecute(new UpdateCommonUpCommand(script, evt.getNewValue().clone())));
		commonUpVector.setToolTipText(
				"Only required if billboard_type is set to perpendicular_self or perpendicular_common, this vector is the common up vector used to orient all particles in the system.");

		// Direction Type (just for experimentation - not used by OGRE
		// system)
		addScrollableContent(new Label("Direction Type", screen));
		directionType = new ComboBox<DirectionType>(screen);
		for (DirectionType o : DirectionType.values()) {
			directionType.addListItem(Icelib.toEnglish(o), o);
		}
		addScrollableContent(directionType);
		directionType.setToolTipText("Not used in real OGRE scripts, just for experimenting with Iceparticles");

		// Billboard Type
		addScrollableContent(new Label("Billboard Type", screen));
		billboardType = new ComboBox<OGREParticleScript.BillboardType>(screen);
		for (OGREParticleScript.BillboardType o : OGREParticleScript.BillboardType.values()) {
			billboardType.addListItem(Icelib.toEnglish(o), o);
		}
		addScrollableContent(billboardType);
		billboardType.setToolTipText("");

		// Billboard Origin
		addScrollableContent(new Label("Billboard Origin", screen));
		billboardOrigin = new ComboBox<OGREParticleScript.BillboardOrigin>(screen);
		for (OGREParticleScript.BillboardOrigin o : OGREParticleScript.BillboardOrigin.values()) {
			billboardOrigin.addListItem(Icelib.toEnglish(o), o);
		}
		addScrollableContent(billboardOrigin);

		// Billboard Rotation
		addScrollableContent(new Label("Billboard Rotation", screen));
		billboardRotation = new ComboBox<OGREParticleScript.BillboardRotation>(screen);
		for (OGREParticleScript.BillboardRotation o : OGREParticleScript.BillboardRotation.values()) {
			billboardRotation.addListItem(Icelib.toEnglish(o), o);
		}
		addScrollableContent(billboardRotation);

		// Cull each
		cullEach = new CheckBox(screen);
		addScrollableContent(cullEach, "gapleft 20, gaptop 20, growx, span 2");
		cullEach.setText("Cull Each");
		cullEach.setToolTipText("All particle systems are culled by the bounding box "
				+ "which contains all the particles in the system. This is normally "
				+ "sufficient for fairly locally constrained particle systems where "
				+ "most particles are either visible or not visible together. However, "
				+ "for those that spread particles over a wider area (e.g. a rain system), "
				+ "you may want to actually cull each particle individually to save on "
				+ "time, since it is far more likely that only a subset of the particles "
				+ "will be visible. You do this by setting the cull_each parameter to true.");

		// Sorted
		sorted = new CheckBox(screen);
		addScrollableContent(sorted, "gapleft 20, growx, span 2");
		sorted.setText("Sorted");
		sorted.setToolTipText("By default, particles are not sorted. By setting this "
				+ "attribute to ’true’, the particles will be sorted with respect to "
				+ "the camera, furthest first. This can make certain rendering effects "
				+ "look better at a small sorting expense.");

		// Local space
		localSpace = new CheckBox(screen);
		addScrollableContent(localSpace, "gapleft 20, span 2");
		localSpace.setText("Local Space");
		localSpace.setToolTipText("By default, particles are emitted into world space, "
				+ "such that if you transform the node to which the system is attached, "
				+ "it will not affect the particles (only the emitters). This tends to "
				+ "give the normal expected behaviour, which is to model how real "
				+ "world particles travel independently from the objects they are "
				+ "emitted from. However, to create some effects you may want the "
				+ "particles to remain attached to the local space the emitter is in "
				+ "and to follow them directly. This option allows you to do that.");

		/* Listen for events */
		quota.onChange(evt -> ScriptEditPanel.this.undoManager
				.storeAndExecute(new UpdateQuotaCommand(script, evt.getNewValue())));
		directionType.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				ScriptEditPanel.this.undoManager
						.storeAndExecute(new UpdateDirectionTypeCommand(script, evt.getNewValue()));
			}
		});
		billboardType.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				ScriptEditPanel.this.undoManager
						.storeAndExecute(new UpdateBillboardTypeCommand(script, evt.getNewValue()));
			}
		});
		billboardOrigin.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				ScriptEditPanel.this.undoManager
						.storeAndExecute(new UpdateBillboardOriginCommand(script, evt.getNewValue()));
			}
		});
		billboardRotation.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				ScriptEditPanel.this.undoManager
						.storeAndExecute(new UpdateBillboardRotationCommand(script, evt.getNewValue()));
			}
		});
		cullEach.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				undoManager.storeAndExecute(new UpdateCullEachCommand(script, evt.getNewValue()));
			}
		});
		sorted.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				undoManager.storeAndExecute(new UpdateSortedCommand(script, evt.getNewValue()));
			}
		});
		localSpace.onChange(evt -> {
			if (!evt.getSource().isAdjusting()) {
				undoManager.storeAndExecute(new UpdateLocalSpaceCommand(script, evt.getNewValue()));
			}
		});

	}

	private Map<String, MaterialList> getMaterialNames() {
		// List<String> materials = new ArrayList<String>();
		// MaterialList ml = app.getAssetManager().loadAsset(new
		// ExtendedMaterialListKey("Effects/particles.material"));
		// for(Map.Entry<String, Material> en : ml.entrySet()) {
		// materials.add(en.getKey());
		// }

		Map<String, MaterialList> paths = new HashMap<String, MaterialList>();
		for (String path : ((ServerAssetManager) ToolKit.get().getApplication().getAssetManager())
				.getAssetNamesMatching("Effects/.*\\.material")) {
			if (path.endsWith(".material")) {
				MaterialList ml = ToolKit.get().getApplication().getAssetManager()
						.loadAsset(new ExtendedMaterialListKey(path));
				for (Map.Entry<String, Material> en : ml.entrySet()) {
					paths.put(en.getKey(), ml);
				}
			}
		}

		// MaterialList ml = app.getAssetManager().loadAsset(new
		// ExtendedMaterialListKey("Effects/particles.material"));
		// materials.add("Effects/particles.material");
		// return materials;

		return paths;
	}

	public void setScript(OGREParticleScript script) {
		this.script = script;

		if (script == null) {
			quota.setEnabled(false);
			material.setEnabled(false);
			commonUpVector.setEnabled(false);
			commonDirection.setEnabled(false);
			localSpace.setEnabled(false);
			cullEach.setEnabled(false);
			sorted.setEnabled(false);
			particleSize.setEnabled(false);
			directionType.setEnabled(false);
			billboardType.setEnabled(false);
			billboardOrigin.setEnabled(false);
			billboardRotation.setEnabled(false);
		} else {
			quota.setEnabled(true);
			material.setEnabled(true);
			commonUpVector.setEnabled(true);
			commonDirection.setEnabled(true);
			localSpace.setEnabled(true);
			sorted.setEnabled(true);
			cullEach.setEnabled(true);
			particleSize.setEnabled(true);
			directionType.setEnabled(true);
			billboardType.setEnabled(true);
			billboardOrigin.setEnabled(true);
			billboardRotation.setEnabled(true);
			quota.setSelectedValue(script.getQuota());
			material.setValue(script.getMaterialName());
			cullEach.runAdjusting(() -> cullEach.setChecked(script.isCullEach()));
			commonUpVector.setValue(script.getCommonUpVector());
			commonDirection.setValue(script.getCommonDirection());
			localSpace.runAdjusting(() -> localSpace.setChecked(script.isLocalSpace()));
			sorted.runAdjusting(() -> sorted.setChecked(script.isSorted()));
			particleSize.setValue(script.getParticleSize());
			directionType.setSelectedByValue(script.getDirectionType());
			billboardType.setSelectedByValue(script.getBillboardType());
			billboardOrigin.setSelectedByValue(script.getBillboardOrigin());
			billboardRotation.runAdjusting(() -> billboardRotation.setSelectedByValue(script.getBillboardRotation()));
		}
	}

	@SuppressWarnings("serial")
	abstract class AbstractScriptCommand implements UndoableCommand {

		protected final OGREParticleScript script;

		public AbstractScriptCommand(OGREParticleScript script) {
			this.script = script;
		}
	}

	@SuppressWarnings("serial")
	class UpdateQuotaCommand extends AbstractScriptCommand {

		private int oldQuota;
		private final int quota;

		public UpdateQuotaCommand(OGREParticleScript script, int quota) {
			super(script);
			this.quota = quota;
		}

		public void undoCommand() {
			script.setNumParticles(oldQuota);
			particleViewer.scriptUpdated(script, new EmitterUpdater() {
				public void update(TimedEmitter emitter) {
					emitter.setMaxParticles(oldQuota);
				}
			});
			ScriptEditPanel.this.quota.setSelectedValue(oldQuota);
		}

		public void doCommand() {
			oldQuota = script.getQuota();
			script.setNumParticles(quota);
			particleViewer.scriptUpdated(script, new EmitterUpdater() {
				public void update(TimedEmitter emitter) {
					emitter.setMaxParticles(quota);
				}
			});
			ScriptEditPanel.this.quota.setSelectedValue(quota);
		}
	}

	@SuppressWarnings("serial")
	class UpdateMaterialCommand extends AbstractScriptCommand {

		private String oldMaterialName;
		private final String newMaterialName;

		public UpdateMaterialCommand(OGREParticleScript script, String newMaterial) {
			super(script);
			this.newMaterialName = newMaterial;
		}

		public void undoCommand() {
			script.setMaterialName(oldMaterialName);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.material.setValue(oldMaterialName);
		}

		public void doCommand() {
			oldMaterialName = script.getMaterialName();
			script.setMaterialName(newMaterialName);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.material.setValue(newMaterialName);
		}
	}

	@SuppressWarnings("serial")
	class UpdateCommonDirectionCommand extends AbstractScriptCommand {

		private Vector3f oldDirection;
		private final Vector3f newDirection;

		public UpdateCommonDirectionCommand(OGREParticleScript script, Vector3f newDirection) {
			super(script);
			this.newDirection = newDirection;
		}

		public void undoCommand() {
			script.getCommonDirection().set(oldDirection);
			ScriptEditPanel.this.commonDirection.setValue(oldDirection);
		}

		public void doCommand() {
			oldDirection = script.getCommonDirection().clone();
			script.getCommonDirection().set(newDirection);
			ScriptEditPanel.this.commonDirection.setValue(newDirection);
		}
	}

	@SuppressWarnings("serial")
	class UpdateCommonUpCommand extends AbstractScriptCommand {

		private Vector3f oldCommonUp;
		private final Vector3f newCommonUp;

		public UpdateCommonUpCommand(OGREParticleScript script, Vector3f newCommonUp) {
			super(script);
			this.newCommonUp = newCommonUp;
		}

		public void undoCommand() {
			script.getCommonDirection().set(oldCommonUp);
			ScriptEditPanel.this.commonUpVector.setValue(oldCommonUp);
		}

		public void doCommand() {
			oldCommonUp = script.getCommonUpVector().clone();
			script.getCommonUpVector().set(newCommonUp);
			ScriptEditPanel.this.commonUpVector.setValue(newCommonUp);
		}
	}

	@SuppressWarnings("serial")
	class UpdateParticleSizeCommand extends AbstractScriptCommand {

		private Vector2f oldSize;
		private final Vector2f newSize;

		public UpdateParticleSizeCommand(OGREParticleScript script, Vector2f newSize) {
			super(script);
			this.newSize = newSize;
		}

		public void undoCommand() {
			script.getParticleSize().set(oldSize);
			ScriptEditPanel.this.particleSize.setValue(oldSize);
		}

		public void doCommand() {
			oldSize = script.getParticleSize().clone();
			script.getParticleSize().set(newSize);
			ScriptEditPanel.this.particleSize.setValue(newSize);
		}
	}

	@SuppressWarnings("serial")
	class UpdateDirectionTypeCommand extends AbstractScriptCommand {

		private DirectionType oldType;
		private final DirectionType newType;

		public UpdateDirectionTypeCommand(OGREParticleScript script, DirectionType newType) {
			super(script);
			this.newType = newType;
		}

		public void undoCommand() {
			script.setDirectionType(oldType);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.directionType
					.runAdjusting(() -> ScriptEditPanel.this.directionType.setSelectedByValue(oldType));
		}

		public void doCommand() {
			oldType = script.getDirectionType();
			script.setDirectionType(newType);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.directionType
					.runAdjusting(() -> ScriptEditPanel.this.directionType.setSelectedByValue(newType));
		}
	}

	@SuppressWarnings("serial")
	class UpdateBillboardTypeCommand extends AbstractScriptCommand {

		private OGREParticleScript.BillboardType oldType;
		private final OGREParticleScript.BillboardType newType;

		public UpdateBillboardTypeCommand(OGREParticleScript script, OGREParticleScript.BillboardType newType) {
			super(script);
			this.newType = newType;
		}

		public void undoCommand() {
			script.setBillboardType(oldType);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardType
					.runAdjusting(() -> ScriptEditPanel.this.billboardType.setSelectedByValue(oldType));
		}

		public void doCommand() {
			oldType = script.getBillboardType();
			script.setBillboardType(newType);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardType
					.runAdjusting(() -> ScriptEditPanel.this.billboardType.setSelectedByValue(newType));
		}
	}

	@SuppressWarnings("serial")
	class UpdateBillboardOriginCommand extends AbstractScriptCommand {

		private OGREParticleScript.BillboardOrigin oldOrigin;
		private final OGREParticleScript.BillboardOrigin newOrigin;

		public UpdateBillboardOriginCommand(OGREParticleScript script, OGREParticleScript.BillboardOrigin newOrigin) {
			super(script);
			this.newOrigin = newOrigin;
		}

		public void undoCommand() {
			script.setBillboardOrigin(oldOrigin);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardOrigin
					.runAdjusting(() -> ScriptEditPanel.this.billboardOrigin.setSelectedByValue(oldOrigin));
		}

		public void doCommand() {
			oldOrigin = script.getBillboardOrigin();
			script.setBillboardOrigin(newOrigin);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardOrigin
					.runAdjusting(() -> ScriptEditPanel.this.billboardOrigin.setSelectedByValue(newOrigin));
		}
	}

	@SuppressWarnings("serial")
	class UpdateBillboardRotationCommand extends AbstractScriptCommand {

		private OGREParticleScript.BillboardRotation oldRotation;
		private final OGREParticleScript.BillboardRotation newRotation;

		public UpdateBillboardRotationCommand(OGREParticleScript script,
				OGREParticleScript.BillboardRotation newRotation) {
			super(script);
			this.newRotation = newRotation;
		}

		public void undoCommand() {
			script.setBillboardRotation(oldRotation);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardRotation
					.runAdjusting(() -> ScriptEditPanel.this.billboardRotation.setSelectedByValue(oldRotation));
		}

		public void doCommand() {
			oldRotation = script.getBillboardRotation();
			script.setBillboardRotation(newRotation);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.billboardRotation
					.runAdjusting(() -> ScriptEditPanel.this.billboardRotation.setSelectedByValue(newRotation));
		}
	}

	@SuppressWarnings("serial")
	class UpdateCullEachCommand extends AbstractScriptCommand {

		private boolean oldCullEach;
		private final boolean newCullEach;

		public UpdateCullEachCommand(OGREParticleScript script, boolean newCullEach) {
			super(script);
			this.newCullEach = newCullEach;
		}

		public void undoCommand() {
			script.setCullEach(oldCullEach);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.cullEach.runAdjusting(() -> ScriptEditPanel.this.cullEach.setChecked(oldCullEach));
		}

		public void doCommand() {
			oldCullEach = script.isCullEach();
			script.setCullEach(newCullEach);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.cullEach.runAdjusting(() -> ScriptEditPanel.this.cullEach.setChecked(newCullEach));
		}
	}

	@SuppressWarnings("serial")
	class UpdateLocalSpaceCommand extends AbstractScriptCommand {

		private boolean oldLocalSpace;
		private final boolean newLocalSpace;

		public UpdateLocalSpaceCommand(OGREParticleScript script, boolean newLocalSpace) {
			super(script);
			this.newLocalSpace = newLocalSpace;
		}

		public void undoCommand() {
			script.setLocalSpace(oldLocalSpace);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.localSpace
					.runAdjusting(() -> ScriptEditPanel.this.localSpace.setChecked(oldLocalSpace));
		}

		public void doCommand() {
			oldLocalSpace = script.isLocalSpace();
			script.setLocalSpace(newLocalSpace);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.localSpace
					.runAdjusting(() -> ScriptEditPanel.this.localSpace.setChecked(newLocalSpace));
		}
	}

	@SuppressWarnings("serial")
	class UpdateSortedCommand extends AbstractScriptCommand {

		private boolean oldSorted;
		private final boolean newSorted;

		public UpdateSortedCommand(OGREParticleScript script, boolean newSorted) {
			super(script);
			this.newSorted = newSorted;
		}

		public void undoCommand() {
			script.setSorted(oldSorted);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.sorted.runAdjusting(() -> ScriptEditPanel.this.sorted.setChecked(oldSorted));
		}

		public void doCommand() {
			oldSorted = script.isSorted();
			script.setSorted(newSorted);
			particleViewer.scriptUpdated(script);
			ScriptEditPanel.this.sorted.runAdjusting(() -> ScriptEditPanel.this.sorted.setChecked(newSorted));
		}
	}
}
