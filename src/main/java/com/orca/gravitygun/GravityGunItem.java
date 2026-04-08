package com.orca.gravitygun;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class GravityGunItem extends Item {
    public GravityGunItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Left-click to grab blocks/entities").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click to launch").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
