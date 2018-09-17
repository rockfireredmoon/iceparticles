package org.iceparticles;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icescene.ogreparticle.OGREParticleAffector;
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
import icetone.core.layout.ScreenLayoutConstraints;
import icetone.core.layout.WrappingLayout;
import icetone.core.layout.mig.MigLayout;
import icetone.core.undo.UndoManager;
import icetone.core.undo.UndoableCommand;
import icetone.extras.windows.DialogBox;

public class AffectorsEditPanel extends SplitPanel {

	private static final Logger LOG = Logger.getLogger(AffectorsEditPanel.class.getName());
	private OGREParticleScript script;
	private final Table affectors;
	private final PropertiesPanel<OGREParticleAffector> properties;
	private final PushButton newAffector;
	private final PushButton deleteAffector;
	private final UndoManager undoManager;
	private final ScrollPanel scroller;
	private final ParticleViewerAppState particleViewer;

	public AffectorsEditPanel(Preferences prefs, ParticleViewerAppState particleViewer, UndoManager undoManager,
			BaseScreen screen) {
		super(screen, Orientation.VERTICAL);

		this.undoManager = undoManager;
		this.particleViewer = particleViewer;

		// Reflection for finding affector implementations
		Reflections emitterReflections = new Reflections(new ConfigurationBuilder()
				.addUrls(ClasspathHelper.forPackage(OGREParticleAffector.class.getPackage().getName()))
				.setScanners(new SubTypesScanner()));
		final Set<Class<? extends OGREParticleAffector>> affectorTypes = emitterReflections
				.getSubTypesOf(OGREParticleAffector.class);

		// New affector
		newAffector = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		newAffector.onMouseReleased(evt -> {
			Menu<Class<? extends OGREParticleAffector>> zm = new Menu<>(screen);
			zm.onChanged((evt2) -> AffectorsEditPanel.this.undoManager
					.storeAndExecute(new NewAffectorCommand(script, evt2.getNewValue().getValue())));
			for (Class<? extends OGREParticleAffector> c : affectorTypes) {
				if (!Modifier.isAbstract(c.getModifiers())) {
					zm.addMenuItem(c.getSimpleName(), c);
				}
			}
			screen.addElement(zm);
			zm.showMenu(null, newAffector.getAbsoluteX() + newAffector.getWidth(), newAffector.getAbsoluteY());
		});
		newAffector.getButtonIcon().setStyleClass("icon icon-new");
		newAffector.setToolTipText("New Emitter");

		// Delete affector

		deleteAffector = new PushButton(screen) {
			{
				setStyleClass("fancy");
			}
		};
		deleteAffector.onMouseReleased(evt -> deleteAffector());
		deleteAffector.getButtonIcon().setStyleClass("icon icon-trash");
		deleteAffector.setToolTipText("Delete Emitter");

		// Toolbar
		StyledContainer tools = new StyledContainer(screen) {
			{
				setStyleClass("editor-tools");
			}
		};
		tools.setLayoutManager(new MigLayout(screen, "wrap 1, ins 0", "[grow, fill]", "[shrink 0][shrink 0]"));
		tools.addElement(newAffector);
		tools.addElement(deleteAffector);

		// Emitters
		affectors = new Table(screen);
		affectors.onChanged((evt) -> {
			if (!evt.getSource().isAdjusting()) {
				rebuildPropertyPane();
			}
		});
		affectors.setHeadersVisible(false);
		affectors.setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
		affectors.addColumn("Group");

		// Top
		StyledContainer top = new StyledContainer(screen);
		top.setLayoutManager(new MigLayout(screen, "ins 0, fill", "[shrink 0][fill, grow]", "[]"));
		top.addElement(tools);
		top.addElement(affectors, "growx, growy");
		top.setMinDimensions(new Size(0, 0));

		// Properties
		properties = new PropertiesPanel<OGREParticleAffector>(screen, prefs) {
			@Override
			protected void onPropertyChange(PropertyInfo<OGREParticleAffector> info, OGREParticleAffector object,
					Object value) {
				AffectorsEditPanel.this.particleViewer.scriptUpdated(object.getScript());
			}
		};
		properties.setUndoManager(undoManager);

		// Scroller
		scroller = new ScrollPanel(screen);
		final WrappingLayout wrappingLayout = new WrappingLayout();
		wrappingLayout.setFill(true);
		wrappingLayout.setOrientation(Orientation.HORIZONTAL);
		scroller.setScrollContentLayout(wrappingLayout);
		scroller.setMinDimensions(new Size(0, 0));
		scroller.addScrollableContent(properties);

		// This
		setLeftOrTop(top);
		setRightOrBottom(scroller);
		setDefaultDividerLocationRatio(0.5f);

	}

	public void setScript(OGREParticleScript script) {
		this.script = script;
		newAffector.setEnabled(script != null);
		deleteAffector.setEnabled(script != null);
		rebuildAffectors();
	}

	protected void deleteAffector() {
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
				AffectorsEditPanel.this.undoManager
						.storeAndExecute(new DeleteAffectorCommand(script, getSelectedAffector()));
				rebuildAffectors();
				hide();
			}
		};
		dialog.setDestroyOnHide(true);
		ElementStyle.warningColor(dialog.getDragBar());
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this affector?"));
		dialog.setResizable(false);
		dialog.setMovable(false);
		dialog.setModal(true);
		screen.showElement(dialog, ScreenLayoutConstraints.center);
	}

	protected void rebuildPropertyPane() {
		OGREParticleAffector affector = getSelectedAffector();
		properties.setObject(affector);
		dirtyLayout(false, LayoutType.boundsChange());
		layoutChildren();
		scroller.scrollToTop();
	}

	protected void rebuildAffectors() {
		affectors.invalidate();
		affectors.removeAllRows();
		if (script != null) {
			for (OGREParticleAffector affector : script.getAffectors()) {
				TableRow row = new TableRow(screen, affectors, affector);
				row.addCell(affector.getClass().getSimpleName(), affector);
				affectors.addRow(row);
			}
		}
		affectors.validate();
		if (affectors.getRowCount() > 0) {
			properties.show();
			runAdjusting(() -> affectors.setSelectedRowIndex(0));
			rebuildPropertyPane();
		} else {
			rebuildPropertyPane();
			properties.hide();
		}
	}

	protected OGREParticleAffector getSelectedAffector() {
		return (OGREParticleAffector) (affectors.isAnythingSelected() ? affectors.getSelectedObjects().get(0) : null);
	}

	@SuppressWarnings("serial")
	class DeleteAffectorCommand implements UndoableCommand {

		private final OGREParticleScript script;
		private final OGREParticleAffector affector;

		public DeleteAffectorCommand(OGREParticleScript script, OGREParticleAffector affector) {
			this.script = script;
			this.affector = affector;
		}

		public void undoCommand() {
			script.getAffectors().add(affector);
			rebuildAffectors();
			affectors.setSelectedRowIndex(affectors.getRowCount() - 1);
		}

		public void doCommand() {
			try {
				script.getAffectors().remove(affector);
				rebuildAffectors();
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to delete emitter.");
			}
		}
	}

	@SuppressWarnings("serial")
	class NewAffectorCommand implements UndoableCommand {

		private final OGREParticleScript script;
		private final Class<? extends OGREParticleAffector> affector;
		private OGREParticleAffector pem;
		private int previousSelection;

		public NewAffectorCommand(OGREParticleScript script, Class<? extends OGREParticleAffector> emitter) {
			this.script = script;
			this.affector = emitter;
		}

		public void undoCommand() {
			script.getAffectors().remove(pem);
			AffectorsEditPanel.this.particleViewer.scriptUpdated(script);
			rebuildAffectors();
			affectors.setSelectedRowIndex(previousSelection);
		}

		public void doCommand() {
			try {
				previousSelection = affectors.getSelectedRowIndex();
				pem = affector.getConstructor(OGREParticleScript.class).newInstance(script);
				script.getAffectors().add(pem);
				AffectorsEditPanel.this.particleViewer.scriptUpdated(script);
				rebuildAffectors();
				affectors.setSelectedRowIndex(affectors.getRowCount() - 1);
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Failed to load affector.", e);
			}
		}
	}
}
