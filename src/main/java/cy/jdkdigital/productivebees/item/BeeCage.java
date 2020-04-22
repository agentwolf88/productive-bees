package cy.jdkdigital.productivebees.item;

import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.entity.bee.ProductiveBeeEntity;
import cy.jdkdigital.productivebees.util.BeeAttribute;
import cy.jdkdigital.productivebees.util.BeeAttributes;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class BeeCage extends Item {

    public BeeCage(Properties properties) {
        super(properties);

        this.addPropertyOverride(new ResourceLocation("filled"), (itemStack, world, entity) -> isFilled(itemStack) ? 1.0F : 0.0F);
    }

    public static boolean isFilled(ItemStack itemStack) {
        return !itemStack.isEmpty() && itemStack.hasTag() && itemStack.getTag().contains("entity");
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        World playerWorld = context.getPlayer().getEntityWorld();
        ItemStack stack = context.getItem();

        if (playerWorld.isRemote() || !isFilled(stack)) {
            return ActionResultType.FAIL;
        }

        World worldIn = context.getWorld();
        BlockPos pos = context.getPos();

        Entity entity = getEntityFromStack(stack, worldIn, true);
        BlockPos blockPos = pos.offset(context.getFace());
        entity.setPositionAndRotation(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5, 0, 0);
        worldIn.addEntity(entity);

        // Delete stack
        context.getPlayer().inventory.deleteStack(stack);

        return ActionResultType.SUCCESS;
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack itemStack, PlayerEntity player, LivingEntity targetIn, Hand hand) {
        if (targetIn.getEntityWorld().isRemote() || (!(targetIn instanceof BeeEntity) || !targetIn.isAlive()) || (isFilled(itemStack))) {
            return false;
        }

        BeeEntity target = (BeeEntity)targetIn;

        CompoundNBT nbt = new CompoundNBT();
        nbt.putString("entity", EntityType.getKey(target.getType()).toString());
        if (target.hasCustomName()) {
            nbt.putString("name", target.getCustomName().getFormattedText());
        } else {
            nbt.putString("name", target.getName().getFormattedText());
        }
        target.writeWithoutTypeId(nbt);

        nbt.putBoolean("isProductiveBee", target instanceof ProductiveBeeEntity);

        String modId = target.getType().getRegistryName().getNamespace();
        nbt.putString("mod", ModList.get().getModObjectById(modId).get().getClass().getSimpleName());

        ItemStack cageStack = new ItemStack(itemStack.getItem());
        cageStack.setTag(nbt);

        itemStack.shrink(1);
        if (itemStack.isEmpty()) {
            player.setHeldItem(hand, cageStack);
        } else if (!player.inventory.addItemStackToInventory(cageStack)) {
            player.dropItem(cageStack, false);
        }

        player.swingArm(hand);

        target.remove(true);

        return true;
    }

    @Nullable
    public Entity getEntityFromStack(ItemStack stack, World world, boolean withInfo) {
        EntityType type = EntityType.byKey(stack.getTag().getString("entity")).orElse(null);
        if (type != null) {
            Entity entity = type.create(world);
            if (withInfo) {
                entity.read(stack.getTag());
            }
            return entity;
        }
        return null;
    }

    @Nonnull
    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        if (!isFilled(stack)) {
            return new TranslationTextComponent(this.getTranslationKey());
        }

        String entityId = stack.getTag().getString("name");
        return new TranslationTextComponent(this.getTranslationKey()).appendText(" (" + entityId + ")");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> list, ITooltipFlag flag) {
        super.addInformation(stack, world, list, flag);

        CompoundNBT tag = stack.getTag();
        if (tag != null) {
            boolean hasStung = tag.getBoolean("HasStung");
            if (hasStung) {
                list.add(new TranslationTextComponent("productivebees.information.health.dying").applyTextStyle(TextFormatting.RED).applyTextStyle(TextFormatting.ITALIC));
            }

            if (tag.getBoolean("isProductiveBee")) {
                String type = tag.getString("bee_type");
                ITextComponent type_value = new TranslationTextComponent("productivebees.information.attribute.type." + type).applyTextStyle(getColor(type));
                list.add((new TranslationTextComponent("productivebees.information.attribute.type", type_value)).applyTextStyle(TextFormatting.DARK_GRAY));

                int productivity = tag.getInt("bee_productivity");
                ITextComponent productivity_value = new TranslationTextComponent(BeeAttributes.keyMap.get(BeeAttributes.PRODUCTIVITY).get(productivity)).applyTextStyle(getColor(productivity));
                list.add((new TranslationTextComponent("productivebees.information.attribute.productivity", productivity_value)).applyTextStyle(TextFormatting.DARK_GRAY));

                int temper = tag.getInt("bee_temper");
                ITextComponent temper_value = new TranslationTextComponent(BeeAttributes.keyMap.get(BeeAttributes.TEMPER).get(temper)).applyTextStyle(getColor(temper));
                list.add((new TranslationTextComponent("productivebees.information.attribute.temper", temper_value)).applyTextStyle(TextFormatting.DARK_GRAY));
            } else {
                String mod = tag.getString("mod");
                list.add((new StringTextComponent("Mod: " + mod)).applyTextStyle(TextFormatting.DARK_AQUA));
            }
        }
    }

    private static TextFormatting getColor(String type) {
        switch (type) {
            case "hive":
                return TextFormatting.YELLOW;
            case "solitary":
                return TextFormatting.GRAY;
        }
        return TextFormatting.WHITE;
    }

    private static TextFormatting getColor(int level) {
        switch (level) {
            case -3:
                return TextFormatting.RED;
            case -2:
                return TextFormatting.DARK_RED;
            case -1:
                return TextFormatting.YELLOW;
            case 1:
                return TextFormatting.GREEN;
            case 2:
                return TextFormatting.BLUE;
            case 3:
                return TextFormatting.GOLD;
        }
        return TextFormatting.LIGHT_PURPLE;
    }
}