package cy.jdkdigital.productivebees.common.block.nest;

import cy.jdkdigital.productivebees.common.block.SolitaryNest;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.dimension.EndDimension;

public class EndStoneNest extends SolitaryNest
{
    public EndStoneNest(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canRepopulateIn(Dimension dimension, Biome biome) {
        return dimension instanceof EndDimension;
    }
}
