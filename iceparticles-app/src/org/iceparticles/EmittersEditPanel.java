package org.iceparticles;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icescene.ogreparticle.AbstractOGREParticleEmitter;
import org.icescene.ogreparticle.OGREParticleEmitter;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.propertyediting.PropertiesPanel;
import org.icescene.propertyediting.PropertyInfo;
import org.iceui.controls.ElementStyle;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;

import icetone.controls.buttons.PushButton;
import icetone.controls.containers.SplitPanel;
import icetone.controls.menuing.Menu;
import icetone.controls.scrolling.ScrollPanel;
import icetone.controls.table.Table;
import icetone.controls.table.TableRow;
import icetone.core.BaseScreen;
import icetone.core.Layout.LayoutType;
import icetone.core.Orientation;
import icetone.core.Size;
import icetone.core.StyledContainer;
import icetone.core.layout.Border;
import icetone.core.layout.BorderLayout;
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.extras.windows.DialogBox;

public class EmittersEditPanel extends StyledContainer {

	private static final Logger LOG = Logger.getLogger(EmittersEditPanel.class.getName());
	private OGREParticleScript script;
	private final Table emitters;
	private final PropertiesPanel<OGREParticleEmitter> properties;
	private final PushButton newEmitter;
	private final PushButton deleteEmitter;
	private final Set<Class<? extends AbstractOGREParticleEmitter>> emitterTypes;
	private final UndoManager undoManager;
	private final ScrollPanel scroller;
	private final ParticleViewerAppState particleViewer;

	public EmittersEditPanel(Preferences prefs, ParticleViewerAppState particleViewer, UndoManager undoManager,
			BaseScreen screen) {
		super(screen);

		this.undoManager = undoManager;
		this.particleViewer = particleViewer;

		// Reflection for finding emitter implementations
		Reflections emitterReflections = new Reflections(new ConfigurationBuilder()
				.addUrls(ClasspathHelper.forPackage(AbstractOGREParticleEmitter.class.getPackage().getName()))
				.setScanners(new SubTypesScanner()));
		emitterTypes = emitterReflections.getSubTypesOf(AbstractOGREParticleEmitter.class);

		// New emitter
		newEmitter = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		newEmitter.onMouseReleased(evt -> createNewEmitter());
		newEmitter.getButtonIcon().setStyleClass("icon icon-new");
		newEmitter.setToolTipText("New Emitter");

		// Delete emitter

		deleteEmitter = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		deleteEmitter.onMouseReleased(evt -> deleteEmitter());
		deleteEmitter.getButtonIcon().setStyleClass("icon icon-trash");
		deleteEmitter.setToolTipText("Delete Emitter");

		// Toolbar
		StyledContainer tools = new StyledContainer(screen) {
			{
				setStyleClass("editor-tools");
			}
		};
		tools.setLayoutManager(new MigLayout(screen, "wrap 1, ins 0", "[grow, fill]", "[shrink 0][shrink 0]"));
		tools.addElement(newEmitter);
		tools.addElement(deleteEmitter);

		// Emitters
		emitters = new Table(screen);
		emitters.onChanged(evt -> {
			if (!evt.getSource().isAdjusting()) {
				rebuildPropertyPane();
			}
		});
		emitters.setHeadersVisible(false);
		emitters.setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
		emitters.addColumn("Group");

		// Top
		StyledContainer top = new StyledContainer(screen);
		top.setLayoutManager(new MigLayout(screen, "ins 0, fill", "[shrink 0][fill, grow]", "[]"));
		top.addElement(tools);
		top.addElement(emitters, "growx, growy");
		top.setMinDimensions(new Size(0, 0));

		// Properties
		properties = new PropertiesPanel<OGREParticleEmitter>(screen, prefs) {
			@Override
			protected void onPropertyChange(PropertyInfo<OGREParticleEmitter> info, OGREParticleEmitter object,
					Object value) {
				EmittersEditPanel.this.particleViewer.scriptUpdated(object.getScript());
			}
		};
		properties.setUndoManager(undoManager);

		// Scroller
		scroller = new ScrollPanel(screen);
		scroller.addScrollableContent(properties);

		// This

		SplitPanel split = new SplitPanel(screen, Orientation.VERTICAL);
		split.setDefaultDividerLocationRatio(0.25f);
		split.setLeftOrTop(top);
		split.setRightOrBottom(scroller);

		setLayoutManager(new BorderLayout());
		addElement(split, Border.CENTER);
	}

	public void setScript(OGREParticleScript script) {
		this.script = script;
		newEmitter.setEnabled(script != null);
		deleteEmitter.setEnabled(script != null);
		rebuildEmitters();
	}

	protected void deleteEmitter() {
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
				EmittersEditPanel.this.undoManager
						.storeAndExecute(new DeleteEmitterCommand(script, getSelectedEmitter()));
				rebuildEmitters();
				hide();
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this emitter?"));
		dialog.setResizable(false);
		dialog.setMovable(false);
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	protected void createNewEmitter() {
		Menu<Class<? extends OGREParticleEmitter>> zm = new Menu<>(screen);
		zm.onChanged((evt) -> {
			try {
				EmittersEditPanel.this.undoManager
						.storeAndExecute(new NewEmitterCommand(script, evt.getNewValue().getValue()));
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to create emitter.", e);
			}
		});
		for (Class<? extends AbstractOGREParticleEmitter> c : emitterTypes) {
			if (!Modifier.isAbstract(c.getModifiers())) {
				zm.addMenuItem(c.getSimpleName(), c);
			}
		}
		screen.addElement(zm);
		zm.showMenu(null, newEmitter.getAbsoluteX() + newEmitter.getWidth(), newEmitter.getAbsoluteY());
	}

	protected void rebuildPropertyPane() {
		OGREParticleEmitter emitter = getSelectedEmitter();
		properties.setObject(emitter);
		dirtyLayout(false, LayoutType.boundsChange());
		layoutChildren();
		scroller.scrollToTop();
	}

	protected void rebuildEmitters() {
		emitters.invalidate();
		emitters.removeAllRows();
		if (script != null) {
			for (OGREParticleEmitter emitter : script.getEmitters()) {
				TableRow row = new TableRow(screen, emitters, emitter);
				row.addCell(emitter.getClass().getSimpleName(), emitter);
				emitters.addRow(row);
			}
		}
		emitters.validate();
		if (emitters.getRowCount() > 0) {
			properties.show();
			emitters.runAdjusting(() -> emitters.setSelectedRowIndex(0));
			rebuildPropertyPane();
		} else {
			rebuildPropertyPane();
			properties.hide();
		}
	}

	protected OGREParticleEmitter getSelectedEmitter() {
		OGREParticleEmitter emitter = (OGREParticleEmitter) (emitters.isAnythingSelected()
				? emitters.getSelectedObjects().get(0) : null);
		return emitter;
	}

	@SuppressWarnings("serial")
	class DeleteEmitterCommand implements UndoableCommand {

		private final OGREParticleScript script;
		private final OGREParticleEmitter emitter;

		public DeleteEmitterCommand(OGREParticleScript script, OGREParticleEmitter emitter) {
			this.script = script;
			this.emitter = emitter;
		}

		public void undoCommand() {
			script.getEmitters().add(emitter);
			particleViewer.scriptUpdated(script);
			rebuildEmitters();
			emitters.setSelectedRowIndex(emitters.getRowCount() - 1);
		}

		public void doCommand() {
			try {
				script.getEmitters().remove(emitter);
				particleViewer.scriptUpdated(script);
				rebuildEmitters();
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to delete emitter.");
			}
		}
	}

	@SuppressWarnings("serial")
	class NewEmitterCommand implements UndoableCommand {

		private final OGREParticleScript script;
		private final Class<? extends OGREParticleEmitter> emitter;
		private OGREParticleEmitter pem;
		private int previousSelection;

		public NewEmitterCommand(OGREParticleScript script, Class<? extends OGREParticleEmitter> emitter) {
			this.script = script;
			this.emitter = emitter;
		}

		public void undoCommand() {
			script.getEmitters().remove(pem);
			particleViewer.scriptUpdated(script);
			rebuildEmitters();
			emitters.setSelectedRowIndex(previousSelection);
		}

		public void doCommand() {
			try {
				previousSelection = emitters.getSelectedRowIndex();
				pem = emitter.getConstructor(OGREParticleScript.class).newInstance(script);
				script.getEmitters().add(pem);
				particleViewer.scriptUpdated(script);
				rebuildEmitters();
				emitters.setSelectedRowIndex(emitters.getRowCount() - 1);
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to load emitter.");
			}
		}
	}
}
