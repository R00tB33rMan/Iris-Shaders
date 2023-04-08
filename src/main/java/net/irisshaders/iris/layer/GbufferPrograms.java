package net.irisshaders.iris.layer;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gbuffer_overrides.matching.SpecialCondition;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

public class GbufferPrograms {
	private static boolean entities;
	private static boolean blockEntities;
	private static boolean outline;
	private static Runnable phaseChangeListener;

	static {
		StateUpdateNotifiers.phaseChangeNotifier = listener -> phaseChangeListener = listener;
	}

	private static void checkReentrancy() {
		if (entities || blockEntities || outline) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call begin function when entities = "
				+ entities + ", blockEntities = " + blockEntities + ", outline = " + outline);
		}
	}

	public static void beginEntities() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.ENTITIES);
		entities = true;
	}

	public static void endEntities() {
		if (!entities) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endEntities when entities = false");
		}

		setPhase(WorldRenderingPhase.NONE);
		entities = false;
	}

	public static void beginOutline() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.OUTLINE);
		outline = true;
	}

	public static void endOutline() {
		if (!outline) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endOutline when outline = false");
		}

		setPhase(WorldRenderingPhase.NONE);
		outline = false;
	}

	public static void beginBlockEntities() {
		checkReentrancy();
		setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
		blockEntities = true;
	}

	public static void endBlockEntities() {
		if (!blockEntities) {
			throw new IllegalStateException("GbufferPrograms in weird state, tried to call endBlockEntities when blockEntities = false");
		}

		setPhase(WorldRenderingPhase.NONE);
		blockEntities = false;
	}

	public static WorldRenderingPhase getCurrentPhase() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			return pipeline.getPhase();
		} else {
			return WorldRenderingPhase.NONE;
		}
	}

	private static void setPhase(WorldRenderingPhase phase) {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setPhase(phase);
		}
	}

	public static void setOverridePhase(WorldRenderingPhase phase) {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

		if (pipeline != null) {
			pipeline.setOverridePhase(phase);
		}
	}

	public static void runPhaseChangeNotifier() {
		if (phaseChangeListener != null) {
			phaseChangeListener.run();
		}
	}

	public static void setupSpecialRenderCondition(SpecialCondition override) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setSpecialCondition(override));
	}

	public static void teardownSpecialRenderCondition(SpecialCondition override) {
		Iris.getPipelineManager().getPipeline().ifPresent(p -> p.setSpecialCondition(null));
	}

	public static void init() {
		// Empty initializer to run static
	}
}