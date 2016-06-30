package org.iceparticles;

import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import org.icescene.ogreparticle.OGREParticleEmitter;
import org.icescene.ogreparticle.OGREParticleScript;
import org.icescene.ogreparticle.TimedEmitter;
import org.icescene.scene.AbstractDebugSceneAppState;

import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

import emitter.Emitter;

public class ParticleViewerAppState extends AbstractDebugSceneAppState {

    private final static Logger LOG = Logger.getLogger(ParticleViewerAppState.class.getName());

    public ParticleViewerAppState(Preferences prefs, Node parentNode) {
        super(prefs, parentNode);
        addPrefKeyPattern(ParticleConfig.PARTICLES_EDITOR + ".*");
    }

    @Override
    protected void handlePrefUpdateSceneThread(PreferenceChangeEvent evt) {
        super.handlePrefUpdateSceneThread(evt);
        if (evt.getKey().equals(ParticleConfig.PARTICLES_DEBUG_EMITTER)
                || evt.getKey().equals(ParticleConfig.PARTICLES_DEBUG_PARTICLES) ||
                evt.getKey().equals(ParticleConfig.PARTICLES_TIME_SCALE)) {
            boolean debugEmitters = prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER,
                    ParticleConfig.PARTICLES_DEBUG_EMITTER_DEFAULT);
            boolean debugParticles = prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_PARTICLES,
                    ParticleConfig.PARTICLES_DEBUG_PARTICLES_DEFAULT);
            float ts = prefs.getFloat(ParticleConfig.PARTICLES_TIME_SCALE, ParticleConfig.PARTICLES_TIME_SCALE_DEFAULT);
            for (Spatial s : parentNode.getChildren()) {
                if (s instanceof Node) {
                    TimedEmitter e = s.getControl(TimedEmitter.class);
                    if (e != null) {
    					e.setTimeScale(ts);
                        e.setEmitterTestMode(debugEmitters, debugParticles);
                    }
                }
            }
        }
    }

    public void addScript(OGREParticleScript group) {
        removeScript(group);
        final String groupKey = getScriptKey(group);
        Node node = new Node(groupKey);
        boolean debugEmitters = prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_EMITTER,
                ParticleConfig.PARTICLES_DEBUG_EMITTER_DEFAULT);
        boolean debugParticles = prefs.getBoolean(ParticleConfig.PARTICLES_DEBUG_PARTICLES,
                ParticleConfig.PARTICLES_DEBUG_PARTICLES_DEFAULT);
        for (OGREParticleEmitter i : group.getEmitters()) {
            final Emitter emitter = i.createEmitter(assetManager);
            LOG.info(String.format("Adding emitter %s to %s", emitter, groupKey));
            emitter.setEnabled(true);
            emitter.setEmitterTestMode(debugEmitters, debugParticles);
            emitter.initialize(assetManager);


            node.addControl(emitter);
        }
        node.setQueueBucket(RenderQueue.Bucket.Transparent);
        parentNode.attachChild(node);
    }

    public boolean hasScript(OGREParticleScript group) {
        final String groupKey = getScriptKey(group);
        return parentNode.getChild(groupKey) != null;
    }

    public void removeScript(OGREParticleScript group) {
        final String groupKey = getScriptKey(group);
        LOG.info(String.format("Removing emitter %s", groupKey));
        Node groupNode = (Node) parentNode.getChild(groupKey);
        if (groupNode != null) {
            groupNode.removeFromParent();
            LOG.info(String.format("Removed emitter %s", groupKey));
        }
    }

    public void scriptUpdated(OGREParticleScript script) {
        if (hasScript(script)) {
            removeScript(script);
            addScript(script);
        }
    }

    public void scriptUpdated(OGREParticleScript script, EmitterUpdater updater) {
        final String groupKey = getScriptKey(script);
        LOG.info(String.format("Removing emitter %s", groupKey));
        Node groupNode = (Node) parentNode.getChild(groupKey);
        if (groupNode != null) {
            TimedEmitter emitter = groupNode.getControl(TimedEmitter.class);
            updater.update(emitter);
        }
    }

    private String getScriptKey(OGREParticleScript group) {
        return "Particle-" + group.getConfiguration().getConfigurationName() + "-" + group.getName();
    }

}
