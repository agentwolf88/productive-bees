package cy.jdkdigital.productivebees.common.block;

import cy.jdkdigital.productivebees.common.tileentity.BottlerTileEntity;
import cy.jdkdigital.productivebees.init.ModTileEntityTypes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ContainerBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Bottler extends ContainerBlock
{
    public static final BooleanProperty HAS_BOTTLE = BooleanProperty.create("has_bottle");

    protected static final VoxelShape SHAPE = VoxelShapes.combineAndSimplify(
            VoxelShapes.fullCube(),
            VoxelShapes.or(
                    makeCuboidShape(0.0D, 0.0D, 3.0D, 16.0D, 3.0D, 13.0D),
                    makeCuboidShape(3.0D, 0.0D, 0.0D, 13.0D, 3.0D, 16.0D),
                    makeCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 3.0D, 15.0D)
            ), IBooleanFunction.ONLY_FIRST);

    public Bottler(Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(HAS_BOTTLE, Boolean.FALSE));
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState oldState, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (oldState.getBlock() != newState.getBlock()) {
            TileEntity tileEntity = worldIn.getTileEntity(pos);
            if (tileEntity instanceof BottlerTileEntity) {
                // Drop inventory
                tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(handler -> {
                    for (int slot = 0; slot < handler.getSlots(); ++slot) {
                        InventoryHelper.spawnItemStack(worldIn, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(slot));
                    }
                });
            }
        }
        super.onReplaced(oldState, worldIn, pos, newState, isMoving);
    }

    @SuppressWarnings("deprecation")
    @Nonnull
    @Override
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        if (!world.isRemote()) {
            final TileEntity tileEntity = world.getTileEntity(pos);

            if (tileEntity != null) {
                ItemStack heldItem = player.getHeldItem(handIn);
                boolean itemUsed = false;

                if (heldItem.getItem() instanceof BucketItem) {
                    if (FluidUtil.interactWithFluidHandler(player, handIn, world, pos, null)) {
                        itemUsed = true;
                    }
                }

                if (!itemUsed) {
                    if (tileEntity instanceof BottlerTileEntity) {
                        openGui((ServerPlayerEntity) player, (BottlerTileEntity) tileEntity);
                    }
                }
            }
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(HAS_BOTTLE);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return ModTileEntityTypes.BOTTLER.get().create();
    }

    @Nullable
    public TileEntity createNewTileEntity(IBlockReader world) {
        return new BottlerTileEntity();
    }

    @Override
    public int tickRate(IWorldReader world) {
        return 40;
    }

    public void openGui(ServerPlayerEntity player, BottlerTileEntity tileEntity) {
        NetworkHooks.openGui(player, tileEntity, packetBuffer -> {
            packetBuffer.writeBlockPos(tileEntity.getPos());
        });
    }
}