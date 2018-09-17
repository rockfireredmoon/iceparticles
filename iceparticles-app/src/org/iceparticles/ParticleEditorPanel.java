package org.iceparticles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icescene.HUDMessageAppState;
import org.icescene.IcesceneApp;
import org.icescene.ogreparticle.OGREParticleConfiguration;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.ogreparticle.emitters.PointEmitter;
import org.iceui.controls.ElementStyle;

import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;

import icetone.controls.buttons.CheckBox;
import icetone.controls.buttons.PushButton;
import icetone.controls.containers.SplitPanel;
import icetone.controls.containers.TabControl;
import icetone.controls.menuing.Menu;
import icetone.controls.table.Table;
import icetone.controls.table.TableCell;
import icetone.controls.table.TableRow;
import icetone.controls.text.Label;
import icetone.controls.text.TextField;
import icetone.core.BaseScreen;
import icetone.core.Orientation;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.ToolKit;
import icetone.core.event.MouseUIButtonEvent;
import icetone.core.layout.FillLayout;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.core.utils.Alarm;
import icetone.extras.windows.DialogBox;
import icetone.extras.windows.InputBox;

public class ParticleEditorPanel extends StyledContainer {

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
	private final PushButton newScript;
	private final PushButton deleteScript;
	private PushButton stopScripts;

	class ScriptTable extends Table {

		public ScriptTable(BaseScreen screen) {
			super(screen);

			setHeadersVisible(false);
			setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
			setSortable(true);
			addColumn("Group");
			addColumn("");
			onMouseReleased(evt -> {
				Menu<ScriptsRightClickMenuAction> rightClickMenu = new Menu<>(screen);
				rightClickMenu.onChanged((evt2) -> {
					switch ((ScriptsRightClickMenuAction) evt2.getNewValue().getValue()) {
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
				});
				rightClickMenu.addMenuItem("Copy", ScriptsRightClickMenuAction.COPY);
				rightClickMenu.addMenuItem("Paste", ScriptsRightClickMenuAction.PASTE);
				rightClickMenu.addSeparator();
				rightClickMenu.addMenuItem("Delete", ScriptsRightClickMenuAction.DELETE);
				rightClickMenu.showMenu(null, evt.getX(), evt.getY());
			}, MouseUIButtonEvent.RIGHT);

			onChanged(evt -> {
				if (!isAdjusting()) {
					rebuildPropertyPane();
				}
			});
		}

	}

	public ParticleEditorPanel(ParticleViewerAppState particleViewer, UndoManager undoManager, BaseScreen screen,
			Preferences prefs, OGREParticleConfiguration configuration) {
		super(screen);
		this.particleViewer = particleViewer;
		this.undoManager = undoManager;
		this.configuration = configuration;

		setLayoutManager(new FillLayout());

		// Table
		scripts = new ScriptTable(screen);
		particleViewer = screen.getApplication().getStateManager().getState(ParticleViewerAppState.class);

		// Scrip parameters
		script = new ScriptEditPanel(prefs, particleViewer, undoManager, screen);

		// Emitters
		emitters = new EmittersEditPanel(prefs, particleViewer, undoManager, screen);

		// Affectors
		affectors = new AffectorsEditPanel(prefs, particleViewer, undoManager, screen);

		// Tabls
		properties = new TabControl(screen);
		properties.setMinDimensions(Size.ZERO);
		properties.addTab("Script", script);
		properties.addTab("Emitters", emitters);
		properties.addTab("Affectors", affectors);

		// New script
		newScript = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		newScript.onMouseReleased(evt -> createNewScript());
		newScript.getButtonIcon().addStyleClass("button-icon icon-new");
		newScript.setToolTipText("New Script");

		// Delete script

		deleteScript = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		deleteScript.onMouseReleased(evt -> deleteScript());
		deleteScript.getButtonIcon().addStyleClass("button-icon icon-trash");
		deleteScript.setToolTipText("Delete Script");
		// Stop all scripts

		stopScripts = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		stopScripts.onMouseReleased(evt -> {
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
					checkBox.setChecked(false);
			}
		});
		stopScripts.getButtonIcon().addStyleClass("button-icon icon-stop");
		stopScripts.setToolTipText("Stop All Scripts");

		// Top
		StyledContainer top = new StyledContainer(screen) {
			{
				setStyleClass("editor-tools");
			}
		};
		top.setLayoutManager(new MigLayout(screen, "wrap 5", "[][fill,grow][][][]", "[shrink 0][fill,grow]"));
		top.addElement(new Label("Filter", screen));
		filter = new TextField(screen) {
			@Override
			public void onKeyRelease(KeyInputEvent evt) {
				super.onKeyRelease(evt);
				resetFilterTimer();
			}
		};
		top.addElement(filter, "growx");
		top.addElement(stopScripts);
		top.addElement(newScript);
		top.addElement(deleteScript);

		top.setMinDimensions(Size.ZERO);
		top.addElement(scripts, "span 5, growx");

		// Split
		split = new SplitPanel(screen, Orientation.VERTICAL);
		split.setLeftOrTop(top);
		split.setRightOrBottom(properties);
		split.setDefaultDividerLocationRatio(0.3f);

		addElement(split);
	}

	public void setConfiguration(OGREParticleConfiguration configuration) {
		this.configuration = configuration;
		rebuild();
	}

	public OGREParticleScript getSelectedScript() {
		TableRow row = (TableRow) scripts.getSelectedRow();
		Object val = row == null ? null : row.getValue();
		if (val instanceof OGREParticleScript) {
			return ((OGREParticleScript) val);
		}
		return null;
	}

	protected void pasteScriptFromClipboard() {
		HUDMessageAppState msg = ToolKit.get().getApplication().getStateManager().getState(HUDMessageAppState.class);
		try {
			OGREParticleConfiguration cfg = new OGREParticleConfiguration("");
			cfg.load(new ByteArrayInputStream(ToolKit.get().getClipboardText().getBytes()));
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
				msg.message(HUDMessageAppState.Channel.INFORMATION,
						String.format("Particle script '%s' pasted from clipboard.", ps.getName()));
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to paste to particle script from clipboad.", e);
		}
	}

	protected void copyScriptToClipboard() {
		OGREParticleScript selectedScript = getSelectedScript();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		HUDMessageAppState msg = ToolKit.get().getApplication().getStateManager().getState(HUDMessageAppState.class);
		try {
			try {
				selectedScript.write(baos, false);
			} finally {
				baos.close();
			}
			ToolKit.get().setClipboardText(new String(baos.toByteArray(), "UTF-8"));
			if (msg != null) {
				msg.message(HUDMessageAppState.Channel.INFORMATION,
						String.format("Particle script '%s' copied to clipboard.", selectedScript.getName()));
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to copy particle file to clipboad.", e);
			if (msg != null) {
				msg.message(HUDMessageAppState.Channel.ERROR, "Failed to copy particle file to clipboad.", e);
			}
		}
	}

	protected void deleteScript() {
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
				hide();
				ParticleEditorPanel.this.undoManager.storeAndExecute(new DeleteScriptCommand(getSelectedScript()));
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this script?"));
		dialog.setResizable(false);
		dialog.setMovable(false);
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	protected void createNewScript() {
		final InputBox dialog = new InputBox(screen, new Vector2f(15, 15), true) {

			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hide();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, String text, boolean toggled) {
				newScript(text);
				hide();
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.setWindowTitle("New Script");
		dialog.setButtonOkText("Create");
		dialog.setMsg("");
		dialog.setResizable(false);
		dialog.setMovable(false);
		dialog.sizeToContent();
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
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
		scripts.invalidate();
		scripts.removeAllRows();
		if (configuration != null) {
			for (Map.Entry<String, OGREParticleScript> g : configuration.getBackingObject().entrySet()) {
				final OGREParticleScript group = g.getValue();

				if (matchesFilter(group.getName())) {

					final TableRow r = new TableRow(screen, scripts, group);

					//
					r.addCell(g.getKey(), g);

					// Active
					TableCell c = new TableCell(screen, group);
					c.setLayoutManager(new MigLayout(screen, "gap 0, ins 0, fill", "[]", "[]"));
					CheckBox active = new CheckBox(screen);
					active.onChange(evt -> {
						boolean toggled = evt.getNewValue();
						for (TableRow cr : r.getChildRows()) {
							TableCell ce = cr.getCell(1);
							CheckBox cb = (CheckBox) ce.getChild(1);
							cb.runAdjusting(() -> cb.setChecked(toggled));
						}
						OGREParticleScript selectedScript = (OGREParticleScript) r.getValue();
						if (toggled)
							scripts.runAdjusting(() -> scripts.setSelectedRowObjects(Arrays.asList(selectedScript)));
						if (particleViewer != null) {
							if (toggled) {
								particleViewer.addScript(selectedScript);
							} else {
								particleViewer.removeScript(selectedScript);
							}
						}
					});
					active.runAdjusting(() -> active.setChecked(
							particleViewer != null && particleViewer.hasScript((OGREParticleScript) r.getValue())));
					c.addElement(active, "ax 50%");
					r.addElement(c);
					scripts.addRow(r);
				}
			}

		}
		scripts.validate();
		if (scripts.getRowCount() > 0) {
			scripts.runAdjusting(() -> {
				scripts.setSelectedRowIndex(0);
				scripts.scrollToSelected();
			});
			properties.show();
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
		filterTask = ((IcesceneApp) ToolKit.get().getApplication()).getAlarm().timed(new Callable<Void>() {
			public Void call() throws Exception {
				rebuild();
				return null;
			}
		}, 2f);
	}

	@SuppressWarnings("serial")
	class NewScriptCommand implements UndoableCommand {

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
	class DeleteScriptCommand implements UndoableCommand {

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
