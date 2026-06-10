package com.example.exanira.item;

import com.example.exanira.client.ClientEventState;
import com.example.exanira.client.EventScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class RadioItem extends Item {

    public RadioItem(Properties properties) {
        super(properties);
    }

    /**
     * Glows (enchantment foil) when the server has flagged this stack as having
     * an active event. The flag is stored in CUSTOM_DATA so it persists in NBT
     * and syncs to the client automatically via the normal inventory update path.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("active");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openScreenIfActive();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreenIfActive() {
        if (ClientEventState.isActive()) {
            Minecraft.getInstance().setScreen(new EventScreen());
        }
    }
}
