package openmods.utils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;

public class FakeBlockAccess implements IBlockAccess {
	private final IBlockState state;
	private final TileEntity tileEntity;

	public FakeBlockAccess(IBlockState state, TileEntity tileEntity) {
		this.state = state;
		this.tileEntity = tileEntity;
	}

	public FakeBlockAccess(IBlockState state) {
		this(state, null);
	}

	private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

	private static boolean isOrigin(BlockPos blockPos) {
		return ORIGIN.equals(blockPos);
	}

	@Override
	public IBlockState getBlockState(BlockPos blockPos) {
		return isOrigin(blockPos)? state : Blocks.air.getDefaultState();
	}

	@Override
	public TileEntity getTileEntity(BlockPos blockPos) {
		return isOrigin(blockPos)? tileEntity : null;
	}

	@Override
	public int getCombinedLight(BlockPos blockPos, int p_72802_4_) {
		return 0xF000F0;
	}

	@Override
	public int getStrongPower(BlockPos pos, EnumFacing direction) {
		return 0;
	}

	@Override
	public boolean isAirBlock(BlockPos blockPos) {
		return !isOrigin(blockPos);
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(BlockPos pos) {
		return BiomeGenBase.desert;
	}

	@Override
	public boolean extendedLevelsInChunkCache() {
		return false;
	}

	@Override
	public boolean isSideSolid(BlockPos blockPos, EnumFacing side, boolean _default) {
		return (isOrigin(blockPos))? state.getBlock().isSideSolid(this, blockPos, side) : _default;
	}

	@Override
	public WorldType getWorldType() {
		return WorldType.DEFAULT;
	}
}