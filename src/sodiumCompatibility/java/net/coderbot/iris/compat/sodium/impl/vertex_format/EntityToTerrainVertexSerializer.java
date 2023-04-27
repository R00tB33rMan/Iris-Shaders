package net.coderbot.iris.compat.sodium.impl.vertex_format;

import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.coderbot.iris.compat.sodium.impl.vertex_format.entity_xhfp.EntityVertex;
import net.coderbot.iris.compat.sodium.impl.vertex_format.entity_xhfp.GlyphVertexExt;
import net.coderbot.iris.vertices.IrisVertexFormats;
import org.lwjgl.system.MemoryUtil;

public class EntityToTerrainVertexSerializer implements VertexSerializer {
	@Override
	public void serialize(long src, long dst, int vertexCount) {
		for(int vertexIndex = 0; vertexIndex < vertexCount; ++vertexIndex) {
			MemoryUtil.memCopy(src, dst, 24);
			MemoryUtil.memPutInt(dst + 24, MemoryUtil.memGetInt(src + 28L));
			MemoryUtil.memPutInt(dst + 28, MemoryUtil.memGetInt(src + 32L));
			MemoryUtil.memPutInt(dst + 32, 0);
			MemoryUtil.memPutInt(dst + 36, MemoryUtil.memGetInt(src + 36L));
			MemoryUtil.memPutInt(dst + 40, MemoryUtil.memGetInt(src + 40L));
			MemoryUtil.memPutInt(dst + 44, MemoryUtil.memGetInt(src + 44L));

			src += EntityVertex.STRIDE;
			dst += IrisVertexFormats.TERRAIN.getVertexSize();
		}

	}
}
