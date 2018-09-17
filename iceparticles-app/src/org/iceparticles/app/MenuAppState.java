package org.iceparticles.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.AppInfo;
import org.icelib.Icelib;
import org.icelib.XDesktop;
import org.iceparticles.ParticleEditorAppState;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.help.HelpAppState;
import org.icescene.ogreparticle.OGREParticleConfiguration;
import org.icescene.options.OptionsAppState;
import org.iceui.actions.ActionAppState;
import org.iceui.actions.ActionMenu;
import org.iceui.actions.ActionMenuBar;
import org.iceui.actions.AppAction;
import org.iceui.controls.ElementStyle;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;

import icemoon.iceloader.ServerAssetManager;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.undo.UndoManager;
import icetone.extras.windows.AlertBox;
import icetone.extras.windows.DialogBox;
import icetone.extras.windows.InputBox;

public class MenuAppState extends IcemoonAppState<IcemoonAppState<?>> {

	private static final Logger LOG = Logger.getLogger(MenuAppState.class.getName());

	private boolean loading;

	private ActionMenuBar menuBar;
	private AppAction close;
	private UndoManager undoManager;

	public MenuAppState(UndoManager undoManager, Preferences prefs) {
		super(prefs);
		this.undoManager = undoManager;
	}

	@Override
	protected void postInitialize() {

		ActionAppState appState = app.getStateManager().getState(ActionAppState.class);
		menuBar = appState.getMenuBar();
		menuBar.invalidate();

		/* Menus */
		menuBar.addActionMenu(new ActionMenu("File", 0));
		menuBar.addActionMenu(new ActionMenu("Particles", 10));
		menuBar.addActionMenu(new ActionMenu("Help", 20));

		/* Actions */
		menuBar.addAction(new AppAction("Open Folder", evt -> openParticlesFolder()).setMenu("File").setMenuGroup(80));
		menuBar.addAction(new AppAction("Options", evt -> toggleOptions()).setMenu("File").setMenuGroup(80));
		menuBar.addAction(new AppAction("Exit", evt -> exitApp()).setMenu("File").setMenuGroup(99));

		/* Environment menu */
		menuBar.addAction(new AppAction("New File", evt -> createNewScript()).setMenu("Particles"));
		menuBar.addAction(new AppAction(new ActionMenu("Open")).setMenu("Particles"));
		menuBar.addAction(close = new AppAction("Close", evt -> closeConfiguration()).setMenu("Environment"));
		menuBar.addAction(new AppAction(new ActionMenu("Configurations")).setMenu("Environment"));

		/* Help Actions */
		menuBar.addAction(new AppAction("Contents", evt -> help()).setMenu("Help"));
		menuBar.addAction(new AppAction("About", evt -> helpAbout()).setMenu("Help"));

		menuBar.validate();

		/* Initial availability */
		loading = true;
		setAvailable();

		/* Background load the terrain menu */

		app.getWorldLoaderExecutorService().execute(new Runnable() {

			@Override
			public String toString() {
				return "Loading available particle files";
			}

			@Override
			public void run() {

				final List<AppAction> actions = new ArrayList<>();

				ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);

				for (String n : ((ServerAssetManager) app.getAssetManager())
						.getAssetNamesMatching(".*/.*\\.particle")) {
					OGREParticleConfiguration cfg = OGREParticleConfiguration.get(assetManager, n);
					if (ped == null || !cfg.equals(ped.getConfiguration())) {
						actions.add(new AppAction(Icelib.getBaseFilename(n), (evt) -> {
							editConfiguration(cfg);
						}).setMenu("Open"));

						if (((IcesceneApp) app).getAssets().isExternal(cfg.getAssetPath())) {
						}
					}
				}

				app.enqueue(new Callable<Void>() {
					@Override
					public Void call() throws Exception {
						menuBar.invalidate();
						actions.forEach((a) -> menuBar.addAction(a));
						menuBar.validate();
						loading = false;
						setAvailable();
						return null;
					}
				});
			}
		});

	}

	private void helpAbout() {
		AlertBox alert = new AlertBox(screen, true) {

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}
		};
		alert.setModal(true);
		alert.setTitle("About");
		alert.setText("<h1>" + AppInfo.getName() + "</h1><h4>Version " + AppInfo.getVersion() + "</h4>");
		screen.showElement(alert, ScreenLayoutConstraints.center);
	}

	protected void closeConfiguration() {
		final ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
		if (ped.isNeedsSave()) {
			final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {

				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					app.getStateManager().detach(ped);
					hide();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Close");
			dialog.setButtonOkText("Close");
			dialog.setText("You have unsaved edits! Are you sure you wish to switch to close this particle file?");
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			app.getStateManager().detach(ped);
		}
	}

	protected File getParticlesFolder() {
		File particlesDir = new File(((IcesceneApp) app).getAssets().getExternalAssetsFolder(), "Particles");
		return particlesDir;
	}

	protected void createNewScript() {
		final InputBox dialog = new InputBox(screen, new Vector2f(15, 15), true) {

			{
				setStyleClass("large");
			}

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				try {
					OGREParticleConfiguration.get(assetManager, String.format("Particles/%s.particle", text));
					error(String.format("A particle file with the name '%s' already exists.", text));
				} catch (AssetNotFoundException anfe) {

					OGREParticleConfiguration cfg = OGREParticleConfiguration
							.create(String.format("Particles/%s", text));
					File particlesDir = getParticlesFolder();
					try {
						if (!particlesDir.exists() && !particlesDir.mkdirs()) {
							throw new IOException("Failed to create " + particlesDir + ".");
						}
						File particleFile = new File(particlesDir, String.format("%s.particle", text));
						if (particleFile.exists()) {
							throw new IOException(String.format("Particle file '%s' already exists.",
									Icelib.privatisePath(particleFile.getAbsolutePath())));
						}
						FileOutputStream fos = new FileOutputStream(particleFile);
						try {
							cfg.write(fos, toggled);
						} finally {
							fos.close();
						}
						info(String.format("Created new particle file '%s'", text));
					} catch (IOException ioe) {
						LOG.log(Level.SEVERE, "Failed to save new particle file.", ioe);
						error("Failed to save new particle file.", ioe);
					} finally {
						((ServerAssetManager) app.getAssetManager()).index();
					}
				}
				hide();
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.setWindowTitle("New Particle File");
		dialog.setButtonOkText("Create");
		dialog.setMsg("");
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	private void help() {
		HelpAppState has = app.getStateManager().getState(HelpAppState.class);
		if (has == null) {
			app.getStateManager().attach(new HelpAppState(prefs));
		} else {
			app.getStateManager().detach(has);
		}
	}

	private void toggleOptions() {
		final OptionsAppState state = stateManager.getState(OptionsAppState.class);
		if (state == null) {
			stateManager.attach(new OptionsAppState(prefs));
		} else {
			stateManager.detach(state);
		}
	}

	protected void openParticlesFolder() {
		final File particlesFolder = getParticlesFolder();
		try {
			XDesktop.getDesktop().open(particlesFolder);
		} catch (IOException ex) {
			LOG.log(Level.SEVERE, String.format("Failed to open particles folder %s", particlesFolder), ex);
			error(String.format("Failed to open particles folder %s", particlesFolder), ex);
		}
	}

	private void exitApp() {
		ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
		if (ped != null && ped.isNeedsSave()) {
			final DialogBox dialog = new DialogBox(screen, true) {

				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					app.stop();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Exit");
			dialog.setButtonOkText("Exit");
			dialog.setText("You have unsaved edits! Are you sure you wish to exit?");
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			app.stop();
		}
	}

	protected void editConfiguration(final OGREParticleConfiguration configuratoin) {
		ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
		if (ped == null) {
			ped = new ParticleEditorAppState(undoManager, prefs, guiNode);
			app.getStateManager().attach(ped);
		}
		if (ped.getConfiguration() != null && ped.isNeedsSave()) {
			final DialogBox dialog = new DialogBox(screen, new Vector2f(15, 15), true) {
				{
					setStyleClass("large");
				}

				@Override
				public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
					hide();
				}

				@Override
				public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
					ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
					ped.setConfiguration(configuratoin);
					hide();
				}
			};
			dialog.setDestroyOnHide(true);
			ElementStyle.warningColor(dialog.getDragBar());
			dialog.setWindowTitle("Confirm Close");
			dialog.setButtonOkText("Close");
			dialog.setText("You have unsaved edits! Are you sure you wish to switch to close this particle file?");
			dialog.setModal(true);
			screen.showElement(dialog, ScreenLayoutConstraints.center);
		} else {
			ped.setConfiguration(configuratoin);
		}
	}

	protected void setAvailable() {
		menuBar.setEnabled(!loading);
		ParticleEditorAppState env = app.getStateManager().getState(ParticleEditorAppState.class);
		close.setEnabled(env != null && env.getConfiguration() != null);
	}

}
