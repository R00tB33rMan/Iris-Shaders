package net.coderbot.iris.compat.sodium.mixin.shadow_map;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager;
import me.jellysquid.mods.sodium.client.util.frustum.Frustum;
import net.coderbot.iris.compat.sodium.impl.shadow_map.ChopChopFrustumCulling;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.compat.sodium.impl.shadow_map.SwappableRenderSectionManager;
import net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum;
import net.coderbot.iris.vendored.joml.Vector4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Modifies {@link RenderSectionManager} to support maintaining a separate visibility list for the shadow camera, as well
 * as disabling chunk rebuilds when computing visibility for the shadow camera.
 */
@Mixin(RenderSectionManager.class)
public class MixinRenderSectionManager implements SwappableRenderSectionManager {
    @Shadow(remap = false)
    @Final
    @Mutable
    private ChunkRenderList chunkRenderList;

    @Shadow(remap = false)
    @Final
    @Mutable
    private ObjectList<RenderSection> tickableChunks;

    @Shadow(remap = false)
    @Final
    @Mutable
    private ObjectList<BlockEntity> visibleBlockEntities;

	@Shadow
	@Final
	private RenderRegionManager regions;

	@Shadow
	@Final
	private Long2ReferenceMap<RenderSection> sections;

	@Shadow(remap = false)
	private boolean needsUpdate;

    @Unique
    private ChunkRenderList chunkRenderListSwap;

    @Unique
    private ObjectList<RenderSection> tickableChunksSwap;

    @Unique
    private ObjectList<BlockEntity> visibleBlockEntitiesSwap;

    @Unique
	private boolean needsUpdateSwap;

    @Unique
    private static final ObjectArrayFIFOQueue<?> EMPTY_QUEUE = new ObjectArrayFIFOQueue<>();

	@Shadow
	private int centerChunkX;
	@Shadow
	private int centerChunkZ;
	@Shadow
	private boolean useOcclusionCulling;
	@Shadow
	private Frustum frustum;
	@Shadow
	private int currentFrame;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void iris$onInit(SodiumWorldRenderer worldRenderer, BlockRenderPassManager renderPassManager,
							 ClientLevel world, int renderDistance, CommandList commandList, CallbackInfo ci) {
        this.chunkRenderListSwap = new ChunkRenderList();
        this.tickableChunksSwap = new ObjectArrayList<>();
        this.visibleBlockEntitiesSwap = new ObjectArrayList<>();
        this.needsUpdateSwap = true;
    }

    @Override
    public void iris$swapVisibilityState() {
        ChunkRenderList chunkRenderListTmp = chunkRenderList;
        chunkRenderList = chunkRenderListSwap;
        chunkRenderListSwap = chunkRenderListTmp;

        ObjectList<RenderSection> tickableChunksTmp = tickableChunks;
        tickableChunks = tickableChunksSwap;
        tickableChunksSwap = tickableChunksTmp;

        ObjectList<BlockEntity> visibleBlockEntitiesTmp = visibleBlockEntities;
        visibleBlockEntities = visibleBlockEntitiesSwap;
        visibleBlockEntitiesSwap = visibleBlockEntitiesTmp;

        boolean needsUpdateTmp = needsUpdate;
        needsUpdate = needsUpdateSwap;
        needsUpdateSwap = needsUpdateTmp;
    }

	@Inject(method = "iterateChunks", at = @At("HEAD"), cancellable = true)
	private void iris$chop2culling(Camera camera, Frustum frustum, int frame, boolean spectator, CallbackInfo ci) {
		if (!(frustum instanceof AdvancedShadowCullingFrustum)) {
			return;
		}

		if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			return;
		}

		if (!Minecraft.getInstance().player.isShiftKeyDown()) {
			return;
		}

		ci.cancel();

		this.currentFrame = frame;
		this.frustum = frustum;
		this.useOcclusionCulling = false;
		BlockPos origin = camera.getBlockPosition();
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		this.centerChunkX = chunkX;
		this.centerChunkZ = chunkZ;

		ChopChopFrustumCulling.addVisibleChunksToRenderList(chunkRenderList, visibleBlockEntities,
			(AdvancedShadowCullingFrustum) frustum, regions, sections, frame);
	}

    @Inject(method = "update", at = @At("RETURN"))
	private void iris$captureVisibleBlockEntities(Camera camera, Frustum frustum, int frame, boolean spectator, CallbackInfo ci) {
		// TODO: Rename injector
		/*if (frustum instanceof AdvancedShadowCullingFrustum) {
			ChunkRenderList chop2 = new ChunkRenderList();

			ChopChopFrustumCulling.addVisibleChunksToRenderList(chop2, visibleBlockEntities,
				(AdvancedShadowCullingFrustum) frustum, regions, sections);

			if (Minecraft.getInstance().player.isShiftKeyDown()) {
				this.chunkRenderList = chop2;
			}
		}*/

		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ShadowRenderer.visibleBlockEntities = visibleBlockEntities;
		}
	}

	@Inject(method = "schedulePendingUpdates", at = @At("HEAD"), cancellable = true, remap = false)
	private void iris$noRebuildEnqueueingInShadowPass(RenderSection section, CallbackInfo ci) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			ci.cancel();
		}
	}

	@Redirect(method = "resetLists", remap = false,
			at = @At(value = "INVOKE", target = "java/util/Collection.iterator ()Ljava/util/Iterator;"))
	private Iterator<?> iris$noQueueClearingInShadowPass(Collection<?> collection) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			return Collections.emptyIterator();
		} else {
			return collection.iterator();
		}
	}

	// TODO: check needsUpdate and needsUpdateSwap patches?
}
