package com.xulai.seedseasons;

import net.minecraft.core.Direction;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

// 能力注册事件跑在模组总线上；不写 bus 参数与项目其余订阅类的写法保持一致。
@EventBusSubscriber(modid = SeedSeasons.MODID)
public final class SeedSeasonsCapabilities {

    private SeedSeasonsCapabilities() {
    }

    /**
     * 只有顶部暴露流体处理器：管道模组从上面注水、也能从上面把水抽走。
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SeedSeasonsBlockEntities.IRRIGATOR.get(),
                (blockEntity, side) -> side == Direction.UP && blockEntity instanceof IrrigatorBlockEntity irrigator
                        ? irrigator.getExposedTank()
                        : null);
    }
}
