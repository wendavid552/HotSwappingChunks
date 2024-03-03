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

package club.mcams.hotswappingchunks.mixins;

import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ThreadedAnvilChunkStorage.class)
public class ThreadedAnvilChunkStorageMixin {
    //交换区块->给区块做标记
    //实验1：检测区块坐标+维度后，会保存多少次？
//    @ModifyArg(
//            method = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;save(Lnet/minecraft/server/world/ChunkHolder;)Z",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/server/world/ThreadedAnvilChunkStorage;save(Lnet/minecraft/world/chunk/Chunk;)Z"
//            ),
//            index = 0
//    )
//    private Chunk modifySave(Chunk chunk) {
//        if (chunk.getPos().equals(new ChunkPos(20, 0))) {
//            System.out.println("Saving chunk at 20, 0");
//            System.out.println(chunk.getStatus());
//        }
//        return chunk;
//    }
}
