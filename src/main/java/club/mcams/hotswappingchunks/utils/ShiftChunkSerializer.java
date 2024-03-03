/*
 * This file is part of the HotSwappingChunks project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2024  WenDavid and contributors
 *
 * HotSwappingChunks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HotSwappingChunks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with HotSwappingChunks.  If not, see <https://www.gnu.org/licenses/>.
 */

package club.mcams.hotswappingchunks.utils;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import static net.minecraft.world.ChunkSerializer.toNbt;

public class ShiftChunkSerializer {
    public static Logger LOGGER = LogUtils.getLogger();
    private static final Codec<PalettedContainer<BlockState>> CODEC = PalettedContainer.createCodec(
		Block.STATE_IDS, BlockState.CODEC, PalettedContainer.PaletteProvider.BLOCK_STATE, Blocks.AIR.getDefaultState()
	);
    public static NbtCompound shiftSerialize(ServerWorld world, Chunk chunk, ChunkPos targetPos) {
		ChunkPos sourcePos = chunk.getPos();
		NbtCompound nbtCompound = new NbtCompound();
		nbtCompound.putInt("DataVersion", SharedConstants.getGameVersion().getWorldVersion());
		nbtCompound.putInt("xPos", targetPos.x);
		nbtCompound.putInt("yPos", chunk.getBottomSectionCoord());
		nbtCompound.putInt("zPos", targetPos.z);
		nbtCompound.putLong("LastUpdate", world.getTime());
		nbtCompound.putLong("InhabitedTime", chunk.getInhabitedTime());
		nbtCompound.putString("Status", chunk.getStatus().getId());
		BlendingData blendingData = chunk.getBlendingData();
		if (blendingData != null) {
			BlendingData.CODEC
				.encodeStart(NbtOps.INSTANCE, blendingData)
				.resultOrPartial(LOGGER::error)
				.ifPresent(nbtElement -> nbtCompound.put("blending_data", nbtElement));
		}

		BelowZeroRetrogen belowZeroRetrogen = chunk.getBelowZeroRetrogen();
		if (belowZeroRetrogen != null) {
			BelowZeroRetrogen.CODEC
				.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen)
				.resultOrPartial(LOGGER::error)
				.ifPresent(nbtElement -> nbtCompound.put("below_zero_retrogen", nbtElement));
		}

		UpgradeData upgradeData = chunk.getUpgradeData();
		if (!upgradeData.isDone()) {
			nbtCompound.put("UpgradeData", upgradeData.toNbt());
		}

		ChunkSection[] chunkSections = chunk.getSectionArray();
		NbtList nbtList = new NbtList();
		LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
		Registry<Biome> registry = world.getRegistryManager().get(Registry.BIOME_KEY);
		Codec<PalettedContainer<RegistryEntry<Biome>>> codec = createCodec(registry);
		boolean bl = chunk.isLightOn();

		for(int i = lightingProvider.getBottomY(); i < lightingProvider.getTopY(); ++i) {
			int j = chunk.sectionCoordToIndex(i);
			boolean bl2 = j >= 0 && j < chunkSections.length;
			ChunkNibbleArray chunkNibbleArray = lightingProvider.get(LightType.BLOCK).getLightSection(ChunkSectionPos.from(sourcePos, i));
			ChunkNibbleArray chunkNibbleArray2 = lightingProvider.get(LightType.SKY).getLightSection(ChunkSectionPos.from(sourcePos, i));
			if (bl2 || chunkNibbleArray != null || chunkNibbleArray2 != null) {
				NbtCompound nbtCompound2 = new NbtCompound();
				if (bl2) {
					ChunkSection chunkSection = chunkSections[j];
					nbtCompound2.put("block_states", CODEC.encodeStart(NbtOps.INSTANCE, chunkSection.getBlockStateContainer()).getOrThrow(false, LOGGER::error));
					nbtCompound2.put("biomes", codec.encodeStart(NbtOps.INSTANCE, chunkSection.getBiomeContainer()).getOrThrow(false, LOGGER::error));
				}

				if (chunkNibbleArray != null && !chunkNibbleArray.isUninitialized()) {
					nbtCompound2.putByteArray("BlockLight", chunkNibbleArray.asByteArray());
				}

				if (chunkNibbleArray2 != null && !chunkNibbleArray2.isUninitialized()) {
					nbtCompound2.putByteArray("SkyLight", chunkNibbleArray2.asByteArray());
				}

				if (!nbtCompound2.isEmpty()) {
					nbtCompound2.putByte("Y", (byte)i);
					nbtList.add(nbtCompound2);
				}
			}
		}

		nbtCompound.put("sections", nbtList);
		if (bl) {
			nbtCompound.putBoolean("isLightOn", true);
		}

		NbtList nbtList2 = new NbtList();

		for(BlockPos blockPos : chunk.getBlockEntityPositions()) {
			NbtCompound nbtCompound3 = chunk.getPackedBlockEntityNbt(blockPos);
			if (nbtCompound3 != null) {
				//方块实体需要修改坐标
				nbtCompound3.putInt("x",nbtCompound3.getInt("x") - sourcePos.getStartX() + targetPos.getStartX());
				nbtCompound3.putInt("z",nbtCompound3.getInt("z") - sourcePos.getStartZ() + targetPos.getStartZ());

				nbtList2.add(nbtCompound3);
			}
		}

		nbtCompound.put("block_entities", nbtList2);
		if (chunk.getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK) {
			ProtoChunk protoChunk = (ProtoChunk)chunk;
			NbtList nbtList3 = new NbtList();
			nbtList3.addAll(protoChunk.getEntities().stream().peek(nbtElement -> {
				NbtList entityPos = nbtElement.getList("Pos", 6);
				nbtElement.put("Pos", toNbtList(entityPos.getDouble(0)-sourcePos.getStartX()+targetPos.getStartX(), entityPos.getDouble(1), entityPos.getDouble(2)-sourcePos.getStartZ()+targetPos.getStartZ()));
            }).toList());
			//实体也需要修改坐标
			nbtCompound.put("entities", nbtList3);

			//LightSource是相对的，不需要修改
			nbtCompound.put("Lights", toNbt(protoChunk.getLightSourcesBySection()));
			NbtCompound nbtCompound3 = new NbtCompound();

			for(GenerationStep.Carver carver : GenerationStep.Carver.values()) {
				CarvingMask carvingMask = protoChunk.getCarvingMask(carver);
				if (carvingMask != null) {
					nbtCompound3.putLongArray(carver.toString(), carvingMask.getMask());
				}
			}

			nbtCompound.put("CarvingMasks", nbtCompound3);
		}

		serializeTicks(world, nbtCompound, chunk.getTickSchedulers());
		nbtCompound.put("PostProcessing", toNbt(chunk.getPostProcessingLists()));
		NbtCompound nbtCompound4 = new NbtCompound();

		for(Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
			if (chunk.getStatus().getHeightmapTypes().contains(entry.getKey())) {
				nbtCompound4.put(((Heightmap.Type)entry.getKey()).getName(), new NbtLongArray(((Heightmap)entry.getValue()).asLongArray()));
			}
		}

		nbtCompound.put("Heightmaps", nbtCompound4);

		//考虑Restore的话不需要看这个，但是如果是强制写入的话需要改这里
		nbtCompound.put("structures", writeStructures(StructureContext.from(world), sourcePos, chunk.getStructureStarts(), chunk.getStructureReferences()));
		return nbtCompound;
	}

    private static Codec<PalettedContainer<RegistryEntry<Biome>>> createCodec(Registry<Biome> biomeRegistry) {
		return PalettedContainer.createCodec(
			biomeRegistry.getIndexedEntries(), biomeRegistry.createEntryCodec(), PalettedContainer.PaletteProvider.BIOME, biomeRegistry.entryOf(BiomeKeys.PLAINS)
		);
	}

    private static void serializeTicks(ServerWorld world, NbtCompound nbt, Chunk.TickSchedulers tickSchedulers) {
		long l = world.getLevelProperties().getTime();
		nbt.put("block_ticks", tickSchedulers.blocks().toNbt(l, block -> Registry.BLOCK.getId(block).toString()));
		nbt.put("fluid_ticks", tickSchedulers.fluids().toNbt(l, fluid -> Registry.FLUID.getId(fluid).toString()));
	}

	public static ChunkStatus.ChunkType getChunkType(@Nullable NbtCompound nbt) {
		return nbt != null ? ChunkStatus.byId(nbt.getString("Status")).getChunkType() : ChunkStatus.ChunkType.PROTOCHUNK;
	}
    private static NbtCompound writeStructures(
		StructureContext context,
		ChunkPos pos,
		Map<ConfiguredStructureFeature<?, ?>, StructureStart> starts,
		Map<ConfiguredStructureFeature<?, ?>, LongSet> references
	) {
		NbtCompound nbtCompound = new NbtCompound();
		NbtCompound nbtCompound2 = new NbtCompound();
		Registry<ConfiguredStructureFeature<?, ?>> registry = context.registryManager().get(Registry.CONFIGURED_STRUCTURE_FEATURE_KEY);

		for(Map.Entry<ConfiguredStructureFeature<?, ?>, StructureStart> entry : starts.entrySet()) {
			Identifier identifier = registry.getId((ConfiguredStructureFeature<?, ?>)entry.getKey());
			nbtCompound2.put(identifier.toString(), ((StructureStart)entry.getValue()).toNbt(context, pos));
		}

		nbtCompound.put("starts", nbtCompound2);
		NbtCompound nbtCompound3 = new NbtCompound();

		for(Map.Entry<ConfiguredStructureFeature<?, ?>, LongSet> entry2 : references.entrySet()) {
			if (!((LongSet)entry2.getValue()).isEmpty()) {
				Identifier identifier2 = registry.getId((ConfiguredStructureFeature<?, ?>)entry2.getKey());
				nbtCompound3.put(identifier2.toString(), new NbtLongArray((LongSet)entry2.getValue()));
			}
		}

		nbtCompound.put("References", nbtCompound3);
		return nbtCompound;
	}

	public static NbtList toNbtList(double... values) {
		NbtList nbtList = new NbtList();

		for(double d : values) {
			nbtList.add(NbtDouble.of(d));
		}

		return nbtList;
	}
}
