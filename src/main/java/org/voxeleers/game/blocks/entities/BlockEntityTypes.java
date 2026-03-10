package org.voxeleers.game.blocks.entities;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.voxeleers.game.blocks.types.BlockType;
import org.voxeleers.game.blocks.types.BlockTypes;

public class BlockEntityTypes {
    public static Object2ObjectOpenHashMap<BlockType, BlockEntity> blockTypeToEntity = new Object2ObjectOpenHashMap<BlockType, BlockEntity>(
            new BlockType[]{
                    BlockTypes.POWERED_VENT
            },
            new BlockEntity[]{
                    new PoweredVentBlockEntity()
            });
}
