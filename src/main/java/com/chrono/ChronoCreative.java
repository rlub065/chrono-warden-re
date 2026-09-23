
package com.chrono;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChronoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChronoCreative {
    @SubscribeEvent
    public static void onCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ChronoMod.CHRONO_ACTIVATOR);
        }
    }
}
