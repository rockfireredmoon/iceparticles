package org.iceparticles;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.UndoManager;
import org.icescene.ogreparticle.OGREParticleAffector;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.propertyediting.PropertiesPanel;
import org.icescene.propertyediting.PropertyInfo;
import org.iceui.controls.FancyButton;
import org.iceui.controls.FancyDialogBox;
import org.iceui.controls.FancyWindow;
import org.iceui.controls.UIUtil;
import org.iceui.controls.ZMenu;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector4f;

import icetone.controls.extras.SplitPanel;
import icetone.controls.lists.Table;
import icetone.controls.scrolling.ScrollPanel;
import icetone.core.Container;
import icetone.core.ElementManager;
import icetone.core.layout.LUtil;
import icetone.core.layout.WrappingLayout;
import icetone.core.layout.mig.MigLayout;

public class AffectorsEditPanel extends SplitPanel {

	private static final Logger LOG = Logger.getLogger(AffectorsEditPanel.class.getName());
	private OGREParticleScript script;
	private boolean adjusting = false;
	private final Table affectors;
	private final PropertiesPanel<OGREParticleAffector> properties;
	private final FancyButton newAffector;
	private final FancyButton deleteAffector;
	private final UndoManager undoManager;
	private final ScrollPanel scroller;
	private final ParticleViewerAppState particleViewer;

	public AffectorsEditPanel(Preferences prefs, ParticleViewerAppState particleViewer, UndoManager undoManager,
			ElementManager screen) {
		super(screen, Vector2f.ZERO, LUtil.LAYOUT_SIZE, Vector4f.ZERO, null, Orientation.VERTICAL);

		this.undoManager = undoManager;
		this.particleViewer = particleViewer;
		adjusting = true;

		// Reflection for finding affector implementations
		Reflections emitterReflections = new Reflections(
				new ConfigurationBuilder().addUrls(ClasspathHelper.forPackage(OGREParticleAffector.class.getPackage().getName()))
						.setScanners(new SubTypesScanner()));
		final Set<Class<? extends OGREParticleAffector>> affectorTypes = emitterReflections
				.getSubTypesOf(OGREParticleAffector.class);

		try {
			// New affector
			newAffector = new FancyButton(screen) {
				@Override
				public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
					ZMenu zm = new ZMenu(screen) {
						@SuppressWarnings("unchecked")
						@Override
						protected void onItemSelected(ZMenu.ZMenuItem item) {
							Class<? extends OGREParticleAffector> clazz = (Class<? extends OGREParticleAffector>) item.getValue();
							AffectorsEditPanel.this.undoManager.storeAndExecute(new NewAffectorCommand(script, clazz));
						}
					};
					for (Class<? extends OGREParticleAffector> c : affectorTypes) {
						if (!Modifier.isAbstract(c.getModifiers())) {
							zm.addMenuItem(c.getSimpleName(), c);
						}
					}
					screen.addElement(zm);
					zm.showMenu(null, newAffector.getAbsoluteX() + newAffector.getWidth(), newAffector.getAbsoluteY());
				}
			};
			newAffector.setButtonIcon(-1, -1, "Interface/Styles/Gold/Common/Icons/new.png");
			newAffector.setToolTipText("New Emitter");

			// Delete affector

			deleteAffector = new FancyButton(screen) {
				@Override
				public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
					deleteAffector();
				}
			};
			deleteAffector.setButtonIcon(-1, -1, "Interface/Styles/Gold/Common/Icons/trash.png");
			deleteAffector.setToolTipText("Delete Emitter");

			// Toolbar
			Container tools = new Container(screen);
			tools.setLayoutManager(new MigLayout(screen, "wrap 1, ins 0", "[grow, fill]", "[shrink 0][shrink 0]"));
			tools.addChild(newAffector);
			tools.addChild(deleteAffector);

			// Emitters
			affectors = new Table(screen) {
				@Override
				public void onChange() {
					if (!adjusting) {
						rebuildPropertyPane();
					}
				}
			};
			affectors.setHeadersVisible(false);
			affectors.setUseContentPaging(true);
			affectors.setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
			affectors.addColumn("Group");

			// Top
			Container top = new Container(screen);
			top.setLayoutManager(new MigLayout(screen, "ins 0, fill", "[shrink 0][fill, grow]", "[]"));
			top.addChild(tools);
			top.addChild(affectors, "growx, growy");
			top.setMinDimensions(new Vector2f(0, 0));

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
			scroller = new ScrollPanel(screen, Vector2f.ZERO, LUtil.LAYOUT_SIZE, Vector4f.ZERO, null);
			final WrappingLayout wrappingLayout = new WrappingLayout();
			wrappingLayout.setFill(true);
			wrappingLayout.setOrientation(Orientation.HORIZONTAL);
			scroller.setScrollContentLayout(wrappingLayout);
			scroller.setMinDimensions(new Vector2f(0, 0));
			scroller.addScrollableContent(properties);

			// This
			setDefaultDividerLocationRatio(0.25f);
			setLeftOrTop(top);
			setRightOrBottom(scroller);

		} finally {
			adjusting = false;
		}
	}

	public void setScript(OGREParticleScript script) {
		this.script = script;
		newAffector.setIsEnabled(script != null);
		deleteAffector.setIsEnabled(script != null);
		rebuildAffectors();
	}

	protected void deleteAffector() {
		final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				AffectorsEditPanel.this.undoManager.storeAndExecute(new DeleteAffectorCommand(script, getSelectedAffector()));
				rebuildAffectors();
				hideWindow();
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this affector?"));

		dialog.sizeToContent();
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	protected void rebuildPropertyPane() {
		OGREParticleAffector affector = getSelectedAffector();
		properties.setObject(affector);
		dirtyLayout(true);
		layoutChildren();
		scroller.scrollToTop();
	}

	protected void rebuildAffectors() {
		affectors.removeAllRows();
		if (script != null) {
			for (OGREParticleAffector affector : script.getAffectors()) {
				Table.TableRow row = new Table.TableRow(screen, affectors, affector);
				row.addCell(affector.getClass().getSimpleName(), affector);
				affectors.addRow(row, false);
			}
		}
		affectors.pack();
		if (affectors.getRowCount() > 0) {
			adjusting = true;
			properties.show();
			try {
				affectors.setSelectedRowIndex(0);
			} finally {
				adjusting = false;
			}
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
	class DeleteAffectorCommand implements UndoManager.UndoableCommand {

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
	class NewAffectorCommand implements UndoManager.UndoableCommand {

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
