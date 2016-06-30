package org.iceparticles;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.icelib.UndoManager;
import org.icescene.ogreparticle.AbstractOGREParticleEmitter;
import org.icescene.ogreparticle.OGREParticleEmitter;
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
import icetone.core.layout.BorderLayout;
import icetone.core.layout.LUtil;
import icetone.core.layout.WrappingLayout;
import icetone.core.layout.mig.MigLayout;

public class EmittersEditPanel extends Container {

	private static final Logger LOG = Logger.getLogger(EmittersEditPanel.class.getName());
	private OGREParticleScript script;
	private boolean adjusting = false;
	private final Table emitters;
	private final PropertiesPanel<OGREParticleEmitter> properties;
	private final FancyButton newEmitter;
	private final FancyButton deleteEmitter;
	private final Set<Class<? extends AbstractOGREParticleEmitter>> emitterTypes;
	private final UndoManager undoManager;
	private final ScrollPanel scroller;
	private final ParticleViewerAppState particleViewer;

	public EmittersEditPanel(Preferences prefs, ParticleViewerAppState particleViewer, UndoManager undoManager,
			ElementManager screen) {
		super(screen);

		adjusting = true;
		this.undoManager = undoManager;
		this.particleViewer = particleViewer;

		// Reflection for finding emitter implementations
		Reflections emitterReflections = new Reflections(new ConfigurationBuilder()
				.addUrls(ClasspathHelper.forPackage(AbstractOGREParticleEmitter.class.getPackage().getName()))
				.setScanners(new SubTypesScanner()));
		emitterTypes = emitterReflections.getSubTypesOf(AbstractOGREParticleEmitter.class);

		try {
			// New emitter
			newEmitter = new FancyButton(screen) {
				@Override
				public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
					createNewEmitter();
				}
			};
			newEmitter.setButtonIcon(-1, -1, "Interface/Styles/Gold/Common/Icons/new.png");
			newEmitter.setToolTipText("New Emitter");

			// Delete emitter

			deleteEmitter = new FancyButton(screen) {
				@Override
				public void onButtonMouseLeftUp(MouseButtonEvent evt, boolean toggled) {
					deleteEmitter();
				}
			};
			deleteEmitter.setButtonIcon(-1, -1, "Interface/Styles/Gold/Common/Icons/trash.png");
			deleteEmitter.setToolTipText("Delete Emitter");

			// Toolbar
			Container tools = new Container(screen);
			tools.setLayoutManager(new MigLayout(screen, "wrap 1, ins 0", "[grow, fill]", "[shrink 0][shrink 0]"));
			tools.addChild(newEmitter);
			tools.addChild(deleteEmitter);

			// Emitters
			emitters = new Table(screen) {
				@Override
				public void onChange() {
					if (!adjusting) {
						rebuildPropertyPane();
					}
				}
			};
			emitters.setHeadersVisible(false);
			emitters.setUseContentPaging(true);
			emitters.setColumnResizeMode(Table.ColumnResizeMode.AUTO_FIRST);
			emitters.addColumn("Group");

			// Top
			Container top = new Container(screen);
			top.setLayoutManager(new MigLayout(screen, "ins 0, fill", "[shrink 0][fill, grow]", "[]"));
			top.addChild(tools);
			top.addChild(emitters, "growx, growy");
			top.setMinDimensions(new Vector2f(0, 0));

			// Properties
			properties = new PropertiesPanel<OGREParticleEmitter>(screen, prefs) {
				@Override
				protected void onPropertyChange(PropertyInfo<OGREParticleEmitter> info, OGREParticleEmitter object, Object value) {
					EmittersEditPanel.this.particleViewer.scriptUpdated(object.getScript());
				}
			};
			properties.setUndoManager(undoManager);

			// Scroller
			scroller = new ScrollPanel(screen, Vector2f.ZERO, LUtil.LAYOUT_SIZE, Vector4f.ZERO, null);
			scroller.addScrollableContent(properties);

			// This

			SplitPanel split = new SplitPanel(screen, Vector2f.ZERO, LUtil.LAYOUT_SIZE, Vector4f.ZERO, null, Orientation.VERTICAL);
			split.setDefaultDividerLocationRatio(0.25f);
			split.setLeftOrTop(top);
			split.setRightOrBottom(scroller);

			setLayoutManager(new BorderLayout());
			addChild(split, BorderLayout.Border.CENTER);

		} finally {
			adjusting = false;
		}
	}

	public void setScript(OGREParticleScript script) {
		this.script = script;
		newEmitter.setIsEnabled(script != null);
		deleteEmitter.setIsEnabled(script != null);
		rebuildEmitters();
	}

	protected void deleteEmitter() {
		final FancyDialogBox dialog = new FancyDialogBox(screen, new Vector2f(15, 15), FancyWindow.Size.LARGE, true) {
			@Override
			public void onButtonCancelPressed(MouseButtonEvent evt, boolean toggled) {
				hideWindow();
			}

			@Override
			public void onButtonOkPressed(MouseButtonEvent evt, boolean toggled) {
				EmittersEditPanel.this.undoManager.storeAndExecute(new DeleteEmitterCommand(script, getSelectedEmitter()));
				rebuildEmitters();
				hideWindow();
			}
		};
		dialog.setDestroyOnHide(true);
		dialog.getDragBar().setFontColor(screen.getStyle("Common").getColorRGBA("warningColor"));
		dialog.getDragBar().setText("Confirm Deletion");
		dialog.setButtonOkText("Delete");
		dialog.setMsg(String.format("Are you sure you wish to delete this emitter?"));

		dialog.sizeToContent();
		dialog.setIsResizable(false);
		dialog.setIsMovable(false);
		UIUtil.center(screen, dialog);
		screen.addElement(dialog, null, true);
		dialog.showAsModal(true);
	}

	protected void createNewEmitter() {
		ZMenu zm = new ZMenu(screen) {
			@SuppressWarnings("unchecked")
			@Override
			protected void onItemSelected(ZMenu.ZMenuItem item) {
				Class<? extends OGREParticleEmitter> clazz = (Class<? extends OGREParticleEmitter>) item.getValue();
				try {
					EmittersEditPanel.this.undoManager.storeAndExecute(new NewEmitterCommand(script, clazz));
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Failed to create emitter.", e);
				}
			}
		};
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
		dirtyLayout(true);
		layoutChildren();
		scroller.scrollToTop();
	}

	protected void rebuildEmitters() {
		System.out.println("rebuildEmitters()");
		emitters.removeAllRows();
		if (script != null) {
			System.out.println(" " + script.getEmitters().size() + " emitters");
			for (OGREParticleEmitter emitter : script.getEmitters()) {
				Table.TableRow row = new Table.TableRow(screen, emitters, emitter);
				row.addCell(emitter.getClass().getSimpleName(), emitter);
				emitters.addRow(row, false);
			}
		}
		emitters.pack();
		if (emitters.getRowCount() > 0) {
			adjusting = true;
			properties.show();
			try {
				emitters.setSelectedRowIndex(0);
			} finally {
				adjusting = false;
			}
			rebuildPropertyPane();
		} else {
			rebuildPropertyPane();
			properties.hide();
		}
	}

	protected OGREParticleEmitter getSelectedEmitter() {
		OGREParticleEmitter emitter = (OGREParticleEmitter) (emitters.isAnythingSelected() ? emitters.getSelectedObjects().get(0)
				: null);
		return emitter;
	}

	@SuppressWarnings("serial")
	class DeleteEmitterCommand implements UndoManager.UndoableCommand {

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
	class NewEmitterCommand implements UndoManager.UndoableCommand {

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
