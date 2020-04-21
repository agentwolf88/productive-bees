package cy.jdkdigital.productivebees.tileentity;

import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.block.AdvancedBeehiveAbstract;
import cy.jdkdigital.productivebees.block.AdvancedBeehive;
import cy.jdkdigital.productivebees.container.AdvancedBeehiveContainer;
import cy.jdkdigital.productivebees.entity.bee.ProductiveBeeEntity;
import cy.jdkdigital.productivebees.init.ModBlocks;
import cy.jdkdigital.productivebees.init.ModTileEntityTypes;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootParameterSets;
import net.minecraft.world.storage.loot.LootParameters;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AdvancedBeehiveTileEntity extends AdvancedBeehiveTileEntityAbstract implements INamedContainerProvider {

    private int tickCounter = 0;
    public static final int BOTTLE_SLOT = 0;
    public static final int[] OUTPUT_SLOTS = new int[] {1,2,3,4,5,6,7,8,9};
    public List<String> inhabitantList = new ArrayList<>();

	private LazyOptional<IItemHandler> inventoryHandler = LazyOptional.of(this::createInventoryHandler);
	private LazyOptional<IItemHandler> bottleHandler = LazyOptional.of(this::createBottleHandler);

	public AdvancedBeehiveTileEntity() {
	    super(ModTileEntityTypes.ADVANCED_BEEHIVE.get());
        MAX_BEES = 3;
	}

    /**
	 * @return The logical-server-side Container for this TileEntity
	 */
	@Nonnull
	@Override
	public Container createMenu(final int windowId, final PlayerInventory playerInventory, final PlayerEntity player) {
		return new AdvancedBeehiveContainer(windowId, playerInventory, this);
	}

	@Override
	public ITextComponent getDisplayName() {
		return new TranslationTextComponent(ModBlocks.ADVANCED_OAK_BEEHIVE.get().getTranslationKey());
	}

	@Override
	public void tick() {
		final World world = this.world;
		if (world == null || world.isRemote()) {
			return;
		}

		if (++tickCounter > ProductiveBeesConfig.GENERAL.itemTickRate.get()) {
            tickCounter = 0;

            ListNBT beeList = ((AdvancedBeehiveTileEntity)world.getTileEntity(this.pos)).getBeeListAsNBTList();
            if (beeList.size() > 0) {
                for (INBT inbt : beeList) {
                    CompoundNBT inb = (CompoundNBT)((CompoundNBT) inbt).get("EntityData");
                    String beeId = inb.getString("id");

                    // TODO, improve performance
                    EntityType<BeeEntity> entityType = (EntityType<BeeEntity>)ForgeRegistries.ENTITIES.getValue(new ResourceLocation(beeId));
                    BeeEntity bee = entityType.create(world);
                    bee.read(inb);

                    Double productionRate = ProductiveBeeEntity.getProductionRate(beeId);

                    // Generate bee produce
                    if (productionRate != null && productionRate > 0) {
                        if (world.rand.nextDouble() < productionRate) {
                            LootTable lootTable = ProductiveBeeEntity.getProductionLootTable(world, beeId);
                            inventoryHandler.ifPresent(inv -> {
                                LootContext ctx =  new LootContext.Builder((ServerWorld) world)
                                    .withRandom(world.rand)
                                    .withParameter(LootParameters.THIS_ENTITY, bee)
                                    .withParameter(LootParameters.POSITION, this.pos)
                                    .withParameter(LootParameters.DAMAGE_SOURCE, DamageSource.CRAMMING)
                                    .build(LootParameterSets.ENTITY);

                                List<ItemStack> stacks = lootTable.generate(ctx);
                                net.minecraftforge.common.ForgeHooks.modifyLoot(stacks, ctx).forEach((stack) -> {
                                    ItemStack insertedStack = ((ItemHandler)inv).addOutput(stack);
                                    ProductiveBees.LOGGER.info("Produced " + stack + " Inserted: " + insertedStack + " " + bee);
                                });
                            });
                        }
                    }
                }
            }

            BlockState blockState = this.getBlockState();

            if (blockState.getBlock() instanceof AdvancedBeehive && blockState.get(AdvancedBeehive.EXPANDED)) {
                int honeyLevel = blockState.get(BeehiveBlock.HONEY_LEVEL);

                // Auto harvest if empty bottles are in
                if (honeyLevel >= 5) {
                    int finalHoneyLevel = honeyLevel;
                    bottleHandler.ifPresent(h -> {
                        ItemStack bottles = h.getStackInSlot(BOTTLE_SLOT);
                        if (!bottles.isEmpty()) {
                            final ItemStack filledBottle = new ItemStack(Items.HONEY_BOTTLE);
                            final ItemStack comb = new ItemStack(Items.HONEYCOMB);
                            inventoryHandler.ifPresent(inv -> {
                                if (!((ItemHandler)inv).addOutput(filledBottle).isEmpty()) {
                                    ((ItemHandler)inv).addOutput(comb);
                                    bottles.shrink(1);
                                    world.setBlockState(pos, blockState.with(BeehiveBlock.HONEY_LEVEL, finalHoneyLevel -5));
                                }
                            });
                        }
                    });
                    honeyLevel = this.world.getBlockState(this.pos).get(BeehiveBlock.HONEY_LEVEL);
                }

                // Update any attached expansion box if the honey level reaches max
                if (honeyLevel >= getMaxHoneyLevel(blockState)) {
                    ((AdvancedBeehive) blockState.getBlock()).updateState(world, this.getPos(), blockState, false);
                }
            }
        }

        super.tick();
	}

	private int getAvailableOutputSlot(IItemHandler handler, ItemStack insertStack) {
	    int emptySlot = 0;
        for (int slot : OUTPUT_SLOTS) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() == insertStack.getItem() && (stack.getMaxStackSize() + insertStack.getCount()) != stack.getCount()) {
                return slot;
            }
            if (stack.isEmpty() && emptySlot == 0) {
                emptySlot = slot;
            }
        }
	    return emptySlot;
    }

    @Override
    public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.getUpdateTag();
        if (this.getBeeListAsNBTList().size() > 0) {
            tag.put("bees", this.getBeeListAsNBTList());
        }
        return tag;
    }

    @Override
    public void read(CompoundNBT tag) {
        super.read(tag);
        CompoundNBT invTag = tag.getCompound("inv");
        CompoundNBT bottleTag = tag.getCompound("bottles");
        inventoryHandler.ifPresent(inv -> ((INBTSerializable<CompoundNBT>) inv).deserializeNBT(invTag));
        bottleHandler.ifPresent(bottle -> ((INBTSerializable<CompoundNBT>) bottle).deserializeNBT(bottleTag));
    }

    @Override
    public CompoundNBT write(CompoundNBT tag) {
        inventoryHandler.ifPresent(inv -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) inv).serializeNBT();
            tag.put("inv", compound);
        });
        bottleHandler.ifPresent(bottle -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) bottle).serializeNBT();
            tag.put("bottles", compound);
        });

        return super.write(tag);
    }

    private IItemHandler createInventoryHandler() {
        return new ItemHandler(10);
    }

    private IItemHandler createBottleHandler() {
        return new ItemHandler(1);
    }

    class ItemHandler extends ItemStackHandler {
        public ItemHandler(int size) {
            super(size);
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            AdvancedBeehiveTileEntity.this.markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot != BOTTLE_SLOT || stack.getItem() == Items.GLASS_BOTTLE;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        public ItemStack addOutput(@Nonnull ItemStack stack) {
            int slot = getAvailableOutputSlot(this, stack);
            if (slot > 0) {
                return insertItem(slot, stack, false);
            }
            return ItemStack.EMPTY;
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
	    return this.getCapability(cap, side, false);
    }

    @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side, @Nullable boolean getBottleHandler) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (getBottleHandler) {
                return bottleHandler.cast();
            }
            return inventoryHandler.cast();
        }
        return super.getCapability(cap, side);
    }
}
