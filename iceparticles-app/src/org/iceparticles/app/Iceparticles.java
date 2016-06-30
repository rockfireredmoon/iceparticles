package org.iceparticles.app;

import icemoon.iceloader.ServerAssetManager;

import java.io.File;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.icelib.AppInfo;
import org.icelib.UndoManager;
import org.iceparticles.ParticleConfig;
import org.iceparticles.ParticleConstants;
import org.iceparticles.ParticleViewerAppState;
import org.icescene.HUDMessageAppState;
import org.icescene.IcesceneApp;
import org.icescene.SceneConstants;
import org.icescene.assets.Assets;
import org.icescene.audio.AudioAppState;
import org.icescene.debug.LoadScreenAppState;
import org.icescene.io.ModifierKeysAppState;
import org.icescene.io.MouseManager;
import org.icescene.options.OptionsAppState;
import org.icescene.ui.WindowManagerAppState;
import org.lwjgl.opengl.Display;

import com.jme3.bullet.BulletAppState;
import com.jme3.input.controls.ActionListener;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class Iceparticles extends IcesceneApp implements ActionListener {

	private final static String MAPPING_OPTIONS = "Options";
	private static final Logger LOG = Logger.getLogger(Iceparticles.class.getName());

	public static void main(String[] args) throws Exception {
		AppInfo.context = Iceparticles.class;

		// Parse command line
		Options opts = createOptions();
		Assets.addOptions(opts);

		CommandLine cmdLine = parseCommandLine(opts, args);

		// A single argument must be supplied, the URL (which is used to
		// deterime router, which in turn locates simulator)
		if (cmdLine.getArgList().isEmpty()) {
			throw new Exception("No URL supplied.");
		}
		Iceparticles app = new Iceparticles(cmdLine);
		startApp(app, cmdLine, "PlanetForever - " + AppInfo.getName() + " - " + AppInfo.getVersion(),
				ParticleConstants.APPSETTINGS_NAME);
	}

	private Iceparticles(CommandLine commandLine) {
		super(ParticleConfig.get(), commandLine, ParticleConstants.APPSETTINGS_NAME, "META-INF/ParticleAssets.cfg");
		setUseUI(true);
		setPauseOnLostFocus(false);
	}

	@Override
	public void restart() {
		Display.setResizable(true);
		super.restart();
	}

	@Override
	public void destroy() {
		super.destroy();
		LOG.info("Destroyed application");
	}

	@Override
	public void onInitialize() {
		getCamera().setFrustumFar(SceneConstants.WORLD_FRUSTUM);

		AmbientLight al = new AmbientLight();
		al.setColor(ColorRGBA.White.mult(3));
		rootNode.addLight(al);

		/*
		 * The scene heirarchy is roughly :-
		 * 
		 * MainCamera      MapCamera
		 *     |              |
		 *    / \             |
		 *                   / \
		 * GameNode         
		 *     |\______ MappableNode
		 *     |              |\_________TerrainNode
		 *     |              \__________SceneryNode
		 *     |
		 *     \_______ WorldNode
		 *                  |\________ClutterNode
		 *                  \_________CreaturesNode
		 */

		flyCam.setMoveSpeed(prefs.getFloat(ParticleConfig.PARTICLES_MOVE_SPEED, ParticleConfig.PARTICLES_MOVE_SPEED_DEFAULT));
		flyCam.setRotationSpeed(prefs
				.getFloat(ParticleConfig.PARTICLES_ROTATE_SPEED, ParticleConfig.PARTICLES_ROTATE_SPEED_DEFAULT));
		flyCam.setZoomSpeed(-prefs.getFloat(ParticleConfig.PARTICLES_ZOOM_SPEED, ParticleConfig.PARTICLES_ZOOM_SPEED_DEFAULT));
		flyCam.setEnabled(true);
		setPauseOnLostFocus(false);
		flyCam.setDragToRotate(true);

		// Scene
		Node gameNode = new Node("Game");
		Node mappableNode = new Node("Mappable");
		gameNode.attachChild(mappableNode);
		Node worldNode = new Node("World");
		gameNode.attachChild(worldNode);
		rootNode.attachChild(gameNode);

		// Undo manager
		UndoManager undoManager = new UndoManager();

		// Environment needs audio (we can also set UI volume now)
		final AudioAppState audioAppState = new AudioAppState(prefs);
		stateManager.attach(audioAppState);
		screen.setUIAudioVolume(audioAppState.getActualUIVolume());

		// Some windows need management
		stateManager.attach(new WindowManagerAppState(prefs));
		
		// Download progress
		LoadScreenAppState load = new LoadScreenAppState(prefs);
		load.setAutoShowOnDownloads(true);
		load.setAutoShowOnTasks(true);
		stateManager.attach(load);

		// Need physics for terrain?
		stateManager.attach(new BulletAppState());

		// For error messages and stuff
		stateManager.attach(new HUDMessageAppState());

		// Mouse manager requires modifier keys to be monitored
		stateManager.attach(new ModifierKeysAppState());

		// Mouse manager for dealing with clicking, dragging etc.
		final MouseManager mouseManager = new MouseManager(rootNode, getAlarm());
		stateManager.attach(mouseManager);

		// A menu
		stateManager.attach(new MenuAppState(undoManager, prefs, gameNode));

		// Viewer handles the active particle groups
		stateManager.attach(new ParticleViewerAppState(prefs, gameNode));

		// Other UI bits (background chooser etc)
		stateManager.attach(new UIAppState(undoManager, prefs));
		

		// Input
		getKeyMapManager().addMapping(MAPPING_OPTIONS);
		getKeyMapManager().addListener(this, MAPPING_OPTIONS);

		// DEBUG INITIAL POSITION

		// getViewPort().setBackgroundColor(ColorRGBA.Gray);

		// cam.setLocation(new Vector3f(-735.6273f, -28.308445f, -298.51944f));
		// cam.setRotation(new Quaternion(-0.014291069f, 0.21937236f,
		// 0.003213677f, 0.9755312f));

		cam.setLocation(new Vector3f(-1.4250787f, 6.5596423f, 47.357388f));
		cam.setRotation(new Quaternion(-1.5846953E-9f, 0.99474746f, 0.102359444f, 8.685237E-9f));

		// screen.setUseToolTips(false);

	}

	@Override
	protected void configureAssetManager(ServerAssetManager serverAssetManager) {
		getAssets().setAssetsExternalLocation(
				System.getProperty("user.home") + File.separator + "Documents" + File.separator + "Iceparticles");
	}

	public void onAction(String name, boolean isPressed, float tpf) {
		if (name.equals(MAPPING_OPTIONS)) {
			if (!isPressed) {
				final OptionsAppState state = stateManager.getState(OptionsAppState.class);
				if (state == null) {
					stateManager.attach(new OptionsAppState(prefs));
				} else {
					stateManager.detach(state);
				}
			}
		}
	}
}
