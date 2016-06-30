package org.iceparticles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.UndoManager;
import org.icescene.Alarm;
import org.icescene.HUDMessageAppState;
import org.icescene.IcesceneApp;
import org.icescene.ogreparticle.OGREParticleConfiguration;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.ogreparticle.emitters.PointEmitter;
import org.iceui.XTabPanelContent;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyInputBox;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.UIUtil;
import org.iceui.controls.XSeparator;
import org.iceui.controls.ZMenu;

import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;

import icetone.controls.buttons.CheckBox;
import icetone.controls.extras.SplitPanel;
import icetone.controls.lists.Table;
import icetone.controls.lists.Table.TableCell;
import icetone.controls.lists.Table.TableRow;
import icetone.controls.text.Label;
import icetone.controls.text.TextField;
import icetone.controls.windows.TabControl;
import icetone.core.Container;
import icetone.core.Element;
import icetone.core.ElementManager;
import icetone.core.layout.FillLayout;
import icetone.core.layout.LUtil;
import icetone.core.layout.mig.MigLayout;
import icetone.listeners.MouseButtonListener;

public class ParticleEditorPanel extends Container {

	private static final Logger LOG = Logger.getLogger(ParticleEditorPanel.class.getName());

	public enum ScriptsRightClickMenuAction {

		DELETE, COPY, PASTE
	}

	private OGREParticleConfiguration configuration;
	private final Table scripts;
	private final ParticleViewerAppState particleViewer;
	private final SplitPanel split;
	private final TabControl properties;
	private final TextField filter;
	private Alarm.AlarmTask filterTask;
	private final ScriptEditPanel script;
	private EmittersEditPanel emitters;
	private final AffectorsEditPanel affectors;
	private final UndoManager undoManager;
	private boolean adjusting;
	private final FancyButton newScript;
	private final FancyButton deleteScript;
	private FancyButton stopScripts;

	class ScriptTable extends Table implements MouseButtonListener {

		public ScriptTable(ElementManager screen) {
			super(screen);
		}

		@Override
		public void onChange() {
			super.onChange();
			if (!adjusting) {
				rebuildPropertyPane();
			}
		}

		@Override
		public void onMouseLeftReleased(MouseButtonEvent evt) {
		}

		@Override
		public void onMouseRightPressed(MouseButtonEvent evt) {
		}

		public void onMouseRightReleased(MouseButtonEvent evt) {
			ZMenu rightClickMenu = new ZMenu(screen) {
				@Override
				protected void onItemSelected(ZMenu.ZMenuItem item) {
					switch ((ScriptsRightClickMenuAction) item.getValue()) {
					case DELETE:
						deleteScript();
						break;
					case COPY:
						copyScriptToClipboard();
						break;
					case PASTE:
						pasteScriptFromClipboard();
						break;
					}
				}
			};
			rightClickMenu.addMenuItem("Copy", ScriptsRightClickMenuAction.COPY);
			rightClickMenu.addMenuItem("Paste", ScriptsRightClickMenuAction.PASTE);
			rightClickMenu.addMenuItem(null, new XSeparator(screen, Element.Orientation.HORIZONTAL), null).setSelectable(false);
			rightClickMenu.addMenuItem("Delete", ScriptsRightClickMenuAction.DELETE);
			screen.addElement(rightClickMenu);
			rightClickMenu.showMenu(null, evt.getX(), evt.getY());
		}

		@Override
		public void onMouseLeftPressed(MouseButtonEvent evt) {
		}
	}

	public ParticleEditorPanel(ParticleViewerAppState particleViewer, UndoManager undoManager, ElementManager screen,
			Preferences prefs, OGREParticleConfiguration configuration) {
		super(screen);
		this.particleViewer = particleViewer;
		this.undoManager = undoManager;
		this.configuration = configuration;

		setLayoutManager(new FillLayout());

		// Table
		scripts = new ScriptTable(screen);
		scripts.setHeadersVisible(false);
		scripts.setUseContentPaging(true);
		scripts.setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
		scripts.setSortable(true);
		scripts.addColumn("Group");
		particleViewer = screen.getApplication().getStateManager().getState(ParticleViewerAppState.class);
		scripts.addColumn("");

		// Scrip parameters
		script = new ScriptEditPanel(prefs, particleViewer, undoManager, screen);

		// Emitters
		emitters = new EmittersEditPanel(prefs, particleViewer, undoManager, screen);

		// Affectors
		affectors = new AffectorsEditPanel(prefs, particleViewer, undoManager, screen);

		// Tabls
		properties = new TabControl(screen);
		properties.setMinDimensions(Vector2f.ZERO);
		properties.addTab("Script");
		properties.addTabChild(0, XTabPanelContent.create(screen, script));
		properties.addTab("Emitters");
		properties.addTabChild(1, XTabPanelContent.create(screen, emitters));
		properties.addTab("Affectors");
		properties.addTabChild(2, XTabPanelContent.create(screen, affectors));

		// New script
		newScript = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				createNewScript();
			}
		};
		newScript.setButtonIcon(16, 16, "Interface/Styles/Gold/Common/Icons/new.png");
		newScript.setToolTipText("New Script");

		// Delete script

		deleteScript = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				deleteScript();
			}
		};
		deleteScript.setButtonIcon(16, 16, "Interface/Styles/Gold/Common/Icons/trash.png");
		deleteScript.setToolTipText("Delete Script");
		// Stop all scripts

		stopScripts = new FancyButton(screen) {
			@Override
			public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
				for (TableRow row : scripts.getRows()) {
					// OGREParticleScript scr =
					// (OGREParticleScript)row.getValue();
					// if(ParticleEditorPanel.this.particleViewer.hasScript(scr))
					// {
					// ParticleEditorPanel.this.particleViewer.removeScript(scr);
					// }
					TableCell cell = row.getCell(1);
					CheckBox checkBox = (CheckBox) cell.getChild("CheckBox:Node");
					if (checkBox != null)
						checkBox.setIsChecked(false);
				}
			}
		};
		stopScripts.setButtonIcon(16, 16, "Interface/Styles/Gold/Common/Icons/stop.png");
		stopScripts.setToolTipText("Stop All Scripts");

		// Top
		Container top = new Container(screen);
		top.setLayoutManager(new MigLayout(screen, "wrap 5", "[][fill,grow][][][]", "[shrink 0][fill,grow]"));
		top.addChild(new Label("Filter", screen));
		filter = new TextField(screen) {
			@Override
			public void onKeyRelease(KeyInputEvent evt) {
				super.onKeyRelease(evt);
				resetFilterTimer();
			}
		};
		top.addChild(filter, "growx");
		top.addChild(stopScripts);
		top.addChild(newScript);
		top.addChild(deleteScript);

		top.setMinDimensions(Vector2f.ZERO);
		top.addChild(scripts, "span 5, growx");

		// Split
		split = new SplitPanel(screen,Vector2f.ZERO, LUtil.LAYOUT_SIZE, Vector4f.ZERO, null, Orientation.VERTICAL);
		split.setLeftOrTop(top);
		split.setRightOrBottom(properties);
		split.setDefaultDividerLocationRatio(0.3f);

		addChild(split);
	}

	public void setConfiguration(OGREParticleConfiguration configuration) {
		this.configuration = configuration;
		rebuild();
	}

	public OGREParticleScript getSelectedScript() {
		Table.TableRow row = (Table.TableRow) scripts.getSelectedRow();
		Object val = row == null ? null : row.getValue();
		if (val instanceof OGREParticleScript) {
			return ((OGREParticleScript) val);
		}
		return null;
	}

	protected void pasteScriptFromClipboard() {
		HUDMessageAppState msg = app.getStateManager().getState(HUDMessageAppState.class);
		try {
			OGREParticleConfiguration cfg = new OGREParticleConfiguration("");
			cfg.load(new ByteArrayInputStream(screen.getClipboardText().getBytes()));
			if (cfg.getBackingObject().isEmpty()) {
				throw new Exception("No script in clipboard.");
			}
			OGREParticleScript ps = cfg.getBackingObject().entrySet().iterator().next().getValue();
			OGREParticleScript sel = getSelectedScript();
			if (sel.getName().equals(ps.getName())) {
				configuration.getBackingObject().remove(sel.getName());
			}
			configuration.getBackingObject().put(ps.getName(), ps);
			rebuild();
			scripts.setSelectedRowIndex(scripts.getRowCount() - 1);
			scripts.scrollToSelected();
			if (msg != null) {
				msg.message(Level.INFO, String.format("Particle script '%s' pasted from clipboard.", ps.getName()));
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to paste to particle script from clipboad.", e);
		}
	}

	protected void copyScriptToClipboard() {
		OGREParticleScript selectedScript = getSelectedScript();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		HUDMessageAppState msg = app.getStateManager().getState(HUDMessageAppState.class);
		try {
			try {
				selectedScript.write(baos, false);
			} finally {
				baos.close();
			}
			screen.setClipboardText(new String(baos.toByteArray(), "UTF-8"));
			if (msg != null) {
				msg.message(Level.INFO, String.format("Particle script '%s' copied to clipboard.", selectedScript.getName()));
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to copy particle file to clipboad.", e);
			if (msg != null) {
				msg.message(Level.SEVERE, "Failed to copy particle file to clipboad.", e);
			}
		}
	}

	protected void deleteScript() {
		final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
				ParticleEditorPanel.this.undoManager.storeAndExecute(new DeleteScriptCommand(getSelectedScript()));
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this script?"));

		dialog.sizeToContent();
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	protected void createNewScript() {
		final FancyInputBox dialog = new FancyInputBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				newScript(text);
				hideWindow();
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.setWindowTitle("New Script");
		dialog.setButtonOkText("Create");
		dialog.setMsg("");
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		dialog.sizeToContent();
		dialog.setWidth(300);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	protected void newScript(String text) {
		OGREParticleScript ps = new OGREParticleScript(text, ParticleEditorPanel.this.configuration);
		ps.setMaterialName("Particles/Burst2");
		final PointEmitter pointEmitter = new PointEmitter(ps);
		pointEmitter.setAngle(30);
		pointEmitter.setTimeToLive(1);
		pointEmitter.setVelocity(100f);
		pointEmitter.setDirection(new Vector3f(0, 1, 0));
		pointEmitter.setStartColour(ColorRGBA.White);
		pointEmitter.setEndColour(ColorRGBA.Red);
		ps.getEmitters().add(pointEmitter);
		ps.setParticleSize(new Vector2f(4, 4));
		ParticleEditorPanel.this.undoManager.storeAndExecute(new NewScriptCommand(ps));
	}

	protected void rebuildPropertyPane() {
		configureScript(getSelectedScript());
	}

	protected void configureScript(OGREParticleScript script) {
		this.script.setScript(script);
		this.emitters.setScript(script);
		this.affectors.setScript(script);
	}

	protected void rebuildParticleScript() {
		properties.setLayoutManager(new MigLayout(screen, "", "[][]", "[]"));
	}

	public void rebuild() {
		scripts.removeAllRows();
		if (configuration != null) {
			for (Map.Entry<String, OGREParticleScript> g : configuration.getBackingObject().entrySet()) {
				final OGREParticleScript group = g.getValue();

				if (matchesFilter(group.getName())) {

					final Table.TableRow r = new Table.TableRow(screen, scripts, group);

					//
					r.addCell(g.getKey(), g);

					// Active
					Table.TableCell c = new Table.TableCell(screen, group);
					c.setLayoutManager(new MigLayout(screen, "gap 0, ins 0, fill", "[]", "[]"));
					CheckBox active = new CheckBox(screen, "CheckBox", Vector2f.ZERO) {
						@Override
						public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
							super.onButtonMouseLeftUp(evt, toggled);
							System.out.println("TOGGLED: " + toggled);
							for (Table.TableRow cr : r.getChildRows()) {
								Table.TableCell c = cr.getCell(1);
								CheckBox cb = (CheckBox) c.getChild(1);
								cb.setIsCheckedNoCallback(toggled);
							}
							OGREParticleScript selectedScript = (OGREParticleScript) r.getValue();
							System.out.println("SEL: " + selectedScript);
							if (toggled)
								scripts.setSelectedRowObjects(Arrays.asList(selectedScript));
							if (particleViewer != null) {
								if (toggled) {
									particleViewer.addScript(selectedScript);
								} else {
									particleViewer.removeScript(selectedScript);
								}
							}
						}
					};
					active.setCheckSize(new Vector2f(14, 14));
					active.setIsCheckedNoCallback(
							particleViewer != null && particleViewer.hasScript((OGREParticleScript) r.getValue()));
					c.addChild(active, "ax 50%");
					r.addChild(c);
					scripts.addRow(r, false);
				}
			}

		}
		scripts.pack();
		if (scripts.getRowCount() > 0) {
			adjusting = true;
			properties.show();
			try {
				scripts.setSelectedRowIndex(0);
				scripts.scrollToSelected();
			} finally {
				adjusting = false;
			}
			rebuildPropertyPane();
		} else {
			properties.hide();
		}
	}

	protected boolean matchesFilter(String name) {
		String filterText = filter.getText().trim().toLowerCase();
		return filterText.equals("") || name.toLowerCase().contains(filterText);
	}

	private void resetFilterTimer() {
		if (filterTask != null) {
			filterTask.cancel();
		}
		filterTask = ((IcesceneApp) app).getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				rebuild();
				return null;
			}
		}, 2f);
	}

	@SuppressWarnings("serial")
	class NewScriptCommand implements UndoManager.UndoableCommand {

		private final OGREParticleScript script;

		public NewScriptCommand(OGREParticleScript script) {
			this.script = script;
		}

		public void undoCommand() {
			script.getConfiguration().removeScript(script);
			rebuild();
		}

		public void doCommand() {
			script.getConfiguration().addScript(script);
			scripts.setSelectedRowIndex(scripts.getRowCount() - 1);
			rebuild();
		}
	}

	@SuppressWarnings("serial")
	class DeleteScriptCommand implements UndoManager.UndoableCommand {

		private final OGREParticleScript script;
		private boolean hadScript;

		public DeleteScriptCommand(OGREParticleScript script) {
			this.script = script;
		}

		public void undoCommand() {
			script.getConfiguration().addScript(script);
			if (hadScript) {
				particleViewer.addScript(script);
			}
			rebuild();
		}

		public void doCommand() {
			hadScript = particleViewer.hasScript(script);
			if (hadScript) {
				particleViewer.removeScript(script);
			}
			script.getConfiguration().removeScript(script);
			rebuild();
		}
	}
}
