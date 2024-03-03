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

package club.mcams.hotswappingchunks;

import club.mcams.hotswappingchunks.mixins.ThreadedAnvilChunkStorageInvoker;
import club.mcams.hotswappingchunks.utils.ShiftChunkSerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestType;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.core.jmx.Server;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class HotSwappingChunksCommand {
    //Command to set the nbt of one chunk to another one
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("hotswappingchunks")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("fromX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("fromZ", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("toX", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("toZ", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            int fromX = IntegerArgumentType.getInteger(context, "fromX");
                                                            int fromZ = IntegerArgumentType.getInteger(context, "fromZ");
                                                            int toX = IntegerArgumentType.getInteger(context, "toX");
                                                            int toZ = IntegerArgumentType.getInteger(context, "toZ");
                                                            try {
                                                                 HotSwappingChunksCommand.executeSetChunks(context.getSource(),fromX, fromZ, toX, toZ);
                                                            } catch (IOException | ExecutionException |
                                                                     InterruptedException e) {
                                                                throw new RuntimeException(e);
                                                            }
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                ));
    }

    private static void executeSetChunks(ServerCommandSource source, int fromX, int fromZ, int toX, int toZ) throws IOException, ExecutionException, InterruptedException {
        if (source.getWorld() instanceof ServerWorld) {
            ServerChunkManager chunkManager = source.getWorld().getChunkManager();
            // Get the chunk nbt from (fromX, fromZ)
            ChunkPos fromPos = new ChunkPos(fromX, fromZ);
            ChunkPos chunkPos = new ChunkPos(toX, toZ);

//            CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> sourceFuture = chunkManager.getChunkFutureSyncOnMainThread(fromX,fromZ,ChunkStatus.FULL,true);
//            sourceFuture.thenAcceptAsync(either -> {
//                if (either.left().isPresent()){
//                    Chunk sourceChunk = either.left().get();
//                    ChunkHolder targetChunkHolder = ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeGetChunkHolder(chunkPos.toLong());
//                    targetChunkHolder.setCompletedChunk(new ReadOnlyChunk(sourceChunk, false));
//                }
//                else{
//                    source.sendError(Text.of("Source chunk not loaded"));
//                }
//            });


//            Chunk sourceChunk = ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeLoadChunk(fromPos);
            NbtCompound sourceChunkNbt = chunkManager.threadedAnvilChunkStorage.getNbt(fromPos);
//            NbtCompound sourceChunkNbt = ShiftChunkSerializer.shiftSerialize(source.getWorld(), chunkManager.getChunk());
            // Modify the nbt to the target chunk
//            source.sendFeedback(Text.of(sourceChunkNbt.toString()), false);
            sourceChunkNbt.putInt("xPos", toX);
            sourceChunkNbt.putInt("zPos", toZ);
            NbtList blockEntities = sourceChunkNbt.getList("block_entities",10).stream().map(NbtCompound.class::cast).collect(NbtList::new, (list, blockEntity) -> {
                blockEntity.putInt("x", blockEntity.getInt("x") + (toX - fromX) * 16);
                blockEntity.putInt("z", blockEntity.getInt("z") + (toZ - fromZ) * 16);
                list.add(blockEntity);
            }, NbtList::addAll);
            sourceChunkNbt.put("block_entities", blockEntities);
            NbtList entities = sourceChunkNbt.getList("entities", 10).stream().map(NbtCompound.class::cast).collect(NbtList::new, (list, entity) -> {
//                entity.putInt("Pos", entity.getInt("Pos") + (toX - fromX) * 16);
//                entity.putInt("Pos", entity.getInt("Pos") + (toZ - fromZ) * 16);
                NbtList pos = entity.getList("Pos",6);
                entity.put("Pos", ShiftChunkSerializer.toNbtList(pos.getDouble(0)+(toX-fromX)*16,pos.getDouble(1),pos.getDouble(2)+(toZ-fromZ)*16));
                list.add(entity);
            }, NbtList::addAll);


            //清除目标区块的poi数据
            PointOfInterestStorage poiStorage = chunkManager.getPointOfInterestStorage();
            poiStorage.getInChunk(PointOfInterestType.ALWAYS_TRUE, chunkPos, PointOfInterestStorage.OccupationStatus.ANY).peek(poi -> poiStorage.remove(poi.getPos()));

            //

            // 方块与blockState数据
            chunkManager.threadedAnvilChunkStorage.setNbt(chunkPos, sourceChunkNbt);


//            if (sourceChunk.isPresent()) {
//                // blockEntity数据
//                sourceChunk.get().getBlockEntities().forEach((blockPos, blockEntity) -> {
//                    if (blockEntity != null) {
//                        source.sendFeedback(Text.of("BlockEntity: " + blockEntity.getPos()), false);
////                        blockEntity.
//                        if (targetChunk != null) {
//                            targetChunk.addBlockEntity(blockEntity);
//                        }
//                        else{
//                            source.sendError(Text.of("Target chunk not loaded"));
//                        }
//                    }
//                });
//                // Entity数据
//                sourceChunk.get().getEntities().forEach(entity -> {
//                    if (entity != null) {
//                        source.sendFeedback(Text.of("Entity: " + entity.getUuidAsString()), false);
//                        if (targetChunk != null) {
//                            targetChunk.addEntity(entity);
//                        }
//                        else{
//                            source.sendError(Text.of("Target chunk not loaded");
//                        }
//                    }
//                });
//            }
//            ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeGetChunkHolder(fromPos.toLong()).
//            Chunk convertedSourceChunk = ChunkSerializer.deserialize(source.getWorld(), chunkManager.getPointOfInterestStorage(), chunkPos, sourceChunkNbt);
//            ChunkHolder sourceChunkHolder = ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeGetChunkHolder(fromPos.toLong());
////            Optional<WorldChunk> sourceChunk = chunkManager.threadedAnvilChunkStorage.makeChunkTickable(sourceChunkHolder).get().left();
//            WorldChunk sourceChunk = chunkManager.getWorldChunk(fromX, fromZ, true);
//            if (!chunkManager.isChunkLoaded(toX, toZ)) {
//
//                chunkManager.threadedAnvilChunkStorage.setNbt(chunkPos, sourceChunkNbt);
////                chunkManager.markForUpdate(chunkPos.getCenterAtY(0));
////                ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeLoadChunk(chunkPos);
//
//                source.sendFeedback(Text.of("Set chunk (" + fromX + ", " + fromZ + ") to (" + toX + ", " + toZ + ")"), false);
//                ChunkHolder targetChunkHolder = ((ThreadedAnvilChunkStorageInvoker)chunkManager.threadedAnvilChunkStorage).invokeGetChunkHolder(chunkPos.toLong());
//                source.sendFeedback(Text.of(targetChunkHolder.getCurrentStatus().toString()),false);
//                if (targetChunkHolder.getCurrentChunk() != null){
//                    source.sendError(Text.of("Target chunk holder still exists"));
//                }
//
//            }
//            else{
//                source.sendError(Text.of("Target chunk still loading"));
//            }
//            ChunkStatus chunkStatus = sourceChunk.getStatus();

            // Set to the target chunks
//            Chunk targetChunk = ;

        }
        else {
            source.sendError(Text.of("Why we get a non-server world?"));
        }
    }
    /*
    /tp 3200 -58 0
    /tp 1000 -58 ~
    /hotswappingchunks set 200 0 201 1
     */
}
