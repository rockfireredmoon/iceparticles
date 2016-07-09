package org.iceparticles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icelib.UndoManager;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.ogreparticle.OGREParticleConfiguration;
import org.icescene.ogreparticle.OGREParticleScript;
import org.iceui.HPosition;
import org.iceui.VPosition;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyInputBox;
import org.iceui.controls.FancyPersistentWindow;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.SaveType;
import org.iceui.controls.UIUtil;

import com.google.common.base.Objects;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

import icemoon.iceloader.ServerAssetManager;
import icetone.core.Element;
import icetone.core.layout.BorderLayout;
import icetone.core.layout.mig.MigLayout;

public class ParticleEditorAppState extends IcemoonAppState<IcemoonAppState<?>> {

	private final static Logger LOG = Logger.getLogger(ParticleEditorAppState.class.getName());

	public static boolean isEditing(AppStateManager stateManager) {
		return stateManager.getState(ParticleEditorAppState.class) != null;
	}

	private FancyPersistentWindow particleEditWindow;
	private ParticleEditorPanel particleEditorPanel;
	private OGREParticleConfiguration particleConfiguration;
	private final UndoManager undoManager;
	private boolean needsSave;
	private UndoManager.ListenerAdapter listener;
	private FancyButton saveEnv;
	private FancyButton copy;
	private FancyButton paste;
	private FancyButton delete;

	public ParticleEditorAppState(UndoManager undoManager, Preferences prefs, Node gameNode) {
		super(prefs);
		this.undoManager = undoManager;
	}

	public boolean isNeedsSave() {
		return needsSave;
	}

	@Override
	protected void postInitialize() {
		undoManager.addListener(listener = new UndoManager.ListenerAdapter() {
			@Override
			protected void change() {
				super.change();
				setAvailable();
				needsSave = true;
			}
		});

		screen = app.getScreen();
		super.postInitialize();
		particleEditWindow();
		particleEditorPanel.rebuild();
		setAvailable();
	}

	@Override
	protected void onCleanup() {
		undoManager.removeListener(listener);
		super.onCleanup();
		setConfiguration(null);
		if (particleEditWindow.getIsVisible()) {
			particleEditWindow.hideWindow();
		}
	}

	public void setConfiguration(OGREParticleConfiguration particleConfiguration) {
		if (!Objects.equal(particleConfiguration, this.particleConfiguration)) {
			needsSave = false;

			// Stop all the particles in the configuration we were editing
			if (this.particleConfiguration != null) {
				ParticleViewerAppState pav = app.getStateManager().getState(ParticleViewerAppState.class);
				if (pav != null) {
					for (Map.Entry<String, OGREParticleScript> en : this.particleConfiguration.getBackingObject().entrySet()) {
						if (pav.hasScript(en.getValue())) {
							pav.removeScript(en.getValue());
						}
					}
				}
			}

			this.particleConfiguration = particleConfiguration;
			if (particleEditorPanel != null) {
				particleEditorPanel.setConfiguration(particleConfiguration);
			}
			if (particleEditWindow != null) {
				setParticleWindowTitle();
			}

			setAvailable();
		}
	}

	public void save() {
		try {
			File file = Icelib.makeParent(app.getAssets().getExternalAssetFile(particleConfiguration.getAssetPath()));
			if (!file.exists()) {
				final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
					@Override
					public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
						hideWindow();
					}

					@Override
					public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
						File particlesDir = getParticlesFolder();
						File newParticleFile = new File(particlesDir, String.format("%s.particle", text));
						boolean existsOnServerOrLocal = false;
						try {
							OGREParticleConfiguration.get(assetManager, String.format("Particles/%s.particle", text));
							existsOnServerOrLocal = true;
						} catch (AssetNotFoundException fne) {
						}
						if (newParticleFile.exists() || existsOnServerOrLocal) {
							error(String.format("A particle file with the name '%s' already exists.", text));
						} else {
							try {
								saveParticles(newParticleFile);
								info(String.format("Saved particle configuration %s", text));
							} catch (Exception e) {
								error(String.format("Faile to save particle configuration %s",
										particleConfiguration.getConfigurationName()), e);
								LOG.log(Level.SEVERE, "Failed to save particle configuration.", e);
							} finally {
								setAvailable();
							}
							hideWindow();
						}
					}
				};
				dialog.setDestroyOnHide(true);
				dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
				dialog.setWindowTitle("Save Particle File");
				dialog.setButtonOkText("Save");
				dialog.setMsg("");
				dialog.setIsResizable(false);
				dialog.setIsMovable(false);
				dialog.sizeToContent();
				dialog.setWidth(340);
				UIUtil.center(screen, dialog);
				screen.addElement(dialog, null, true);
				dialog.showAsModal(true);
			} else {
				saveParticles(file);
				info(String.format("Saved particle configuration %s", particleConfiguration.getConfigurationName()));
			}
		} catch (Exception e) {
			error(String.format("Faile to save particle configuration %s", particleConfiguration.getConfigurationName()), e);
			LOG.log(Level.SEVERE, "Failed to save particle configuration.", e);
		} finally {
			setAvailable();
		}
	}

	public OGREParticleConfiguration getConfiguration() {
		return particleConfiguration;
	}

	protected void particleEditWindow() {
		particleEditWindow = new FancyPersistentWindow(screen, "ParticleEdit",
				screen.getStyle("Common").getInt("defaultWindowOffset"), VPosition.TOP, HPosition.RIGHT, new Vector2f(410, 480),
				FancyWindow.Size.SMALL, true, SaveType.POSITION_AND_SIZE, prefs) {
			@Override
			protected void onCloseWindow() {
				super.onCloseWindow();
				app.getStateManager().detach(ParticleEditorAppState.this);
			}
		};
		particleEditWindow.setIsResizable(true);
		particleEditWindow.setDestroyOnHide(true);
		setParticleWindowTitle();
		particleEditWindow.setMinimizable(true);

		Element contentArea = particleEditWindow.getContentArea();
		contentArea.setLayoutManager(new BorderLayout(4, 4));
		contentArea.addChild(
				particleEditorPanel = new ParticleEditorPanel(app.getStateManager().getState(ParticleViewerAppState.class),
						undoManager, screen, prefs, particleConfiguration),
				"span 4, growx");
		contentArea.addChild(createButtons(), BorderLayout.Border.SOUTH);
		screen.addElement(particleEditWindow);
		if (particleConfiguration != null) {
			setConfiguration(particleConfiguration);
		}
	}

	protected Element createButtons() {
		Element bottom = new Element(screen);
		bottom.setLayoutManager(new MigLayout(screen, "", "push[][][][]push", "[]"));

		// Save
		saveEnv = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				save();
			}
		};
		saveEnv.setToolTipText("Save any changes you have made.");
		saveEnv.setText("Save");
		bottom.addChild(saveEnv);

		// Copy
		copy = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try {
					try {
						particleConfiguration.write(baos, false);
					} finally {
						baos.close();
					}
					screen.setClipboardText(new String(baos.toByteArray(), "UTF-8"));
					info(String.format("Particle file '%s' copied to clipboard.", particleConfiguration.getConfigurationName()));
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to copy particle file to clipboad.", e);
				}
			}
		};
		copy.setText("Copy");
		copy.setToolTipText("Copy the entire script file to the clipboard.");
		bottom.addChild(copy);

		// Paste
		paste = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				try {
					OGREParticleConfiguration cfg = particleConfiguration;
					cfg.load(new ByteArrayInputStream(screen.getClipboardText().getBytes()));
					setConfiguration(null);
					setConfiguration(cfg);
					info(String.format("Particle file '%s' pasted from clipboard.", cfg.getConfigurationName()));
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to paste to particle file from clipboad.", e);
				}
			}
		};
		paste.setText("Paste");
		paste.setToolTipText("Paste the entire script file to the clipboard.");
		bottom.addChild(paste);

		// Delete
		delete = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				deleteScriptFile();
			}
		};
		delete.setText("Delete");
		delete.setToolTipText("Delete the entire script file.");
		bottom.addChild(delete);

		return bottom;
	}

	protected void setAvailable() {
		if (app == null) {
			return;
		}
		boolean external = particleConfiguration != null
				&& ((IcesceneApp) app).getAssets().isExternal(particleConfiguration.getAssetPath());
		delete.setIsEnabled(external);
		paste.setIsEnabled(external);
		saveEnv.setIsEnabled(needsSave);
	}

	protected void deleteScriptFile() {
		final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				((IcesceneApp) app).getAssets().getExternalAssetFile(particleConfiguration.getAssetPath()).delete();
				((ServerAssetManager) app.getAssetManager()).reindex();
				OGREParticleConfiguration.removeFromCache(particleConfiguration);
				app.getStateManager().detach(ParticleEditorAppState.this);
				hideWindow();
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.getDragBar().setText("Confirm Script File Delete");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this script file?"));

		dialog.pack(false);
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	private void setParticleWindowTitle() {
		if (particleConfiguration == null) {
			particleEditWindow.setWindowTitle(String.format("Particles - <None>"));
		} else {
			particleEditWindow.setWindowTitle(String.format("Particles - %s", particleConfiguration.getConfigurationName()));
		}
	}

	protected File getParticlesFolder() {
		File particlesDir = new File(((IcesceneApp) app).getAssets().getExternalAssetsFolder(), "Particles");
		return particlesDir;
	}

	protected void saveParticles(File file) throws IOException, FileNotFoundException {
		LOG.info(String.format("Writing %s to %s", particleConfiguration.getConfigurationName(), file));
		FileOutputStream fos = new FileOutputStream(file);
		try {
			particleConfiguration.write(fos, false);
		} finally {
			((ServerAssetManager) app.getAssetManager()).index();
			fos.close();
			needsSave = false;
		}
	}
}
