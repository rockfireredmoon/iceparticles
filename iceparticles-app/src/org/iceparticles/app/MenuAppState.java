package org.iceparticles.app;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.Icelib;
import org.icelib.UndoManager;
import org.icelib.XDesktop;
import org.iceparticles.ParticleEditorAppState;
import org.icescene.IcemoonAppState;
import org.icescene.IcesceneApp;
import org.icescene.help.HelpAppState;
import org.icescene.ogreparticle.OGREParticleConfiguration;
import org.icescene.options.OptionsAppState;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyInputBox;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.UIUtil;
import org.iceui.controls.XSeparator;
import org.iceui.controls.ZMenu;

import com.jme3.asset.AssetNotFoundException;
import com.jme3.font.BitmapFont.VAlign;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;

import icemoon.iceloader.ServerAssetManager;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.Element.ZPriority;
import icetone.core.layout.FlowLayout;
import icetone.core.layout.GridLayout;
import icetone.core.layout.LUtil;

public class MenuAppState extends IcemoonAppState<IcemoonAppState> {

    public enum MenuActions {

        NEW_PARTICLE_FILE, OPEN_PARTICLES_FOLDER
    }
    private static final Logger LOG = Logger.getLogger(MenuAppState.class.getName());
    private Container layer;
    private FancyButton particleConfigurations;
    private FancyButton options;
    private FancyButton exit;
    private FancyButton help;
    private final UndoManager undoManager;

    public MenuAppState(UndoManager undoManager, Preferences prefs, Node gameNode) {
        super(prefs);
        this.undoManager = undoManager;
    }

    @Override
    protected void postInitialize() {

        layer = new Container(screen);
        layer.getTextPaddingVec().set(0, 0, 40, 0);
        FlowLayout layoutManager = new FlowLayout();
        layoutManager.setValign(VAlign.Top);
		layer.setLayoutManager(layoutManager);

        // Particle Configurations
        particleConfigurations = new FancyButton(screen) {
            @Override
            public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
            	((IcesceneApp)app).getWorldLoaderExecutorService().execute(new Runnable() {
					@Override
					public void run() {
		                ZMenu menu = createParticleConfiguration();
		                app.enqueue(new Callable<Void>() {
							@Override
							public Void call() throws Exception {
						        screen.addElement(menu);
				                menu.showMenu(null, getAbsoluteX(), LUtil.getAbsoluteY(particleConfigurations) + particleConfigurations.getHeight());
								return null;
							}
						});
					}

					@Override
					public String toString() {
						return "Finding Particle Resources";
					}
				});
            }
        };
        particleConfigurations.setText("Particles");
        layer.addChild(particleConfigurations);

        // Options
        options = new FancyButton(screen) {
            @Override
            public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
                toggleOptions();
            }
        };
        options.setText("Options");
        layer.addChild(options);

        // Help
        help = new FancyButton(screen) {
            @Override
            public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
                help();
            }
        };
        help.setText("Help");
        layer.addChild(help);

        // Exit
        exit = new FancyButton(screen) {
            @Override
            public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
                exitApp();
            }
        };
        exit.setText("Exit");
        layer.addChild(exit);

        //
        app.getLayers(ZPriority.MENU).addChild(layer);
    }

    @Override
    protected void onCleanup() {
        app.getLayers(ZPriority.MENU).removeChild(layer);
    }

    protected void editConfiguration(final OGREParticleConfiguration configuratoin) {
        ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
        if (ped == null) {
            ped = new ParticleEditorAppState(undoManager, prefs, guiNode);
            app.getStateManager().attach(ped);
        }
        if (ped.getConfiguration() != null && ped.isNeedsSave()) {
            final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
                @Override
                public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
                    hideWindow();
                }

                @Override
                public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
                    ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
                    ped.setConfiguration(configuratoin);
                    hideWindow();
                }
            };
            dialog.setDestroyOnHide(true);
            dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
            dialog.setWindowTitle("Confirm Close");
            dialog.setButtonOkText("Close");
            dialog.setMsg("You have unsaved edits! Are you sure you wish to switch to close this particle file?");

            dialog.setIsResizable(false);
            dialog.setIsMovable(false);
            dialog.sizeToContent();
            UIUtil.center(screen, dialog);
    		screen.addElement(dialog, null, true);
            dialog.showAsModal(true);
        } else {
            ped.setConfiguration(configuratoin);
        }
    }

    protected File getParticlesFolder() {
        File particlesDir = new File(((IcesceneApp) app).getAssets().getExternalAssetsFolder(), "Particles");
        return particlesDir;
    }

    protected void createNewScript() {
        final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
            @Override
            public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
                hideWindow();
            }

            @Override
            public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
                try {
                    OGREParticleConfiguration.get(assetManager, String.format("Particles/%s.particle", text));
                    error(String.format("A particle file with the name '%s' already exists.", text));
                } catch (AssetNotFoundException anfe) {

                    OGREParticleConfiguration cfg = OGREParticleConfiguration.create(String.format("Particles/%s", text));
                    File particlesDir = getParticlesFolder();
                    try {
                        if (!particlesDir.exists() && !particlesDir.mkdirs()) {
                            throw new IOException("Failed to create " + particlesDir + ".");
                        }
                        File particleFile = new File(particlesDir, String.format("%s.particle", text));
                        if (particleFile.exists()) {
                            throw new IOException(String.format(
                                    "Particle file '%s' already exists.",
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
                hideWindow();
            }
        };
        dialog.setDestroyOnHide(true);
        dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
        dialog.setWindowTitle("New Particle File");
        dialog.setButtonOkText("Create");
        dialog.setMsg("");
        dialog.setIsResizable(false);
        dialog.setIsMovable(false);
        dialog.sizeToContent();
        dialog.setWidth(340);
        UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
        dialog.showAsModal(true);
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

    private ZMenu createParticleConfiguration() {
        ZMenu menu = new ZMenu(screen) {
            @Override
            public void onItemSelected(ZMenu.ZMenuItem item) {
                super.onItemSelected(item);
                if (item.getValue().equals(MenuActions.NEW_PARTICLE_FILE)) {
                    createNewScript();
                } else if (item.getValue().equals(MenuActions.OPEN_PARTICLES_FOLDER)) {
                    final File particlesFolder = getParticlesFolder();
                    try {
                    	XDesktop.getDesktop().open(particlesFolder);
                    } catch (IOException ex) {
                        LOG.log(Level.SEVERE, String.format("Failed to open particles folder %s", particlesFolder), ex);
                        error(String.format("Failed to open particles folder %s", particlesFolder), ex);
                    }
                } else if (item.getValue().equals(Boolean.FALSE)) {

                    final ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
                    if (ped.isNeedsSave()) {
                        final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
                            @Override
                            public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
                                hideWindow();
                            }

                            @Override
                            public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
                                app.getStateManager().detach(ped);
                                hideWindow();
                            }
                        };
                        dialog.setDestroyOnHide(true);
                        dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
                        dialog.setWindowTitle("Confirm Close");
                        dialog.setButtonOkText("Close");
                        dialog.setMsg("You have unsaved edits! Are you sure you wish to switch to close this particle file?");

                        dialog.setIsResizable(false);
                        dialog.setIsMovable(false);
                        dialog.sizeToContent();
                        UIUtil.center(screen, dialog);
                		screen.addElement(dialog, null, true);
                        dialog.showAsModal(true);
                    } else {
                        app.getStateManager().detach(ped);
                    }
                } else {
                    final OGREParticleConfiguration configuratoin = (OGREParticleConfiguration) item.getValue();
                    editConfiguration(configuratoin);
                }
            }
        };
        for (MenuActions n : MenuActions.values()) {
            menu.addMenuItem(Icelib.toEnglish(n), n);
        }
        menu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);

        ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);

        for (String n : ((ServerAssetManager) app.getAssetManager()).getAssetNamesMatching(".*/.*\\.particle")) {
            OGREParticleConfiguration cfg = OGREParticleConfiguration.get(assetManager, n);
            if (ped == null || !cfg.equals(ped.getConfiguration())) {
                ZMenu.ZMenuItem m = menu.addMenuItem(Icelib.getBaseFilename(n), cfg);
                if (((IcesceneApp) app).getAssets().isExternal(cfg.getAssetPath())) {
                    m.getItemTextElement().setFontColor(ColorRGBA.Green);
                }
            }
        }
        if (ped != null && ped.getConfiguration() != null) {
            menu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);
            menu.addMenuItem("Close", Boolean.FALSE);
        }

        // Show menu
        return menu;
    }

    private void exitApp() {
        ParticleEditorAppState ped = app.getStateManager().getState(ParticleEditorAppState.class);
        if (ped != null && ped.isNeedsSave()) {
            final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
                @Override
                public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
                    hideWindow();
                }

                @Override
                public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
                    app.stop();
                }
            };
            dialog.setDestroyOnHide(true);
            dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
            dialog.setWindowTitle("Confirm Exit");
            dialog.setButtonOkText("Exit");
            dialog.setMsg("You have unsaved edits! Are you sure you wish to exit?");

            dialog.setIsResizable(false);
            dialog.setIsMovable(false);
            dialog.sizeToContent();
            UIUtil.center(screen, dialog);
    		screen.addElement(dialog, null, true);
            dialog.showAsModal(true);
        } else {
            app.stop();
        }
    }
}
