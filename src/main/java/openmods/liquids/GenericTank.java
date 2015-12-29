package openmods.liquids;

import java.util.*;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;
import openmods.utils.BlockUtils;
import openmods.utils.CollectionUtils;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class GenericTank extends FluidTank {

	private List<EnumFacing> surroundingTanks = Lists.newArrayList();
	private final IFluidFilter filter;

	public interface IFluidFilter {
		public boolean canAcceptFluid(FluidStack stack);
	}

	private static final IFluidFilter NO_RESTRICTIONS = new IFluidFilter() {
		@Override
		public boolean canAcceptFluid(FluidStack stack) {
			return true;
		}
	};
	private static final Function<Fluid, FluidStack> FLUID_CONVERTER = new Function<Fluid, FluidStack>() {
		@Override
		@Nullable
		public FluidStack apply(@Nullable Fluid input) {
			return new FluidStack(input, 0);
		}
	};

	private static IFluidFilter filter(final FluidStack... acceptableFluids) {
		if (acceptableFluids.length == 0) return NO_RESTRICTIONS;

		return new IFluidFilter() {
			@Override
			public boolean canAcceptFluid(FluidStack stack) {
				for (FluidStack acceptableFluid : acceptableFluids)
					if (acceptableFluid.isFluidEqual(stack)) return true;

				return false;
			}
		};
	}

	public GenericTank(int capacity) {
		super(capacity);
		this.filter = NO_RESTRICTIONS;
	}

	public GenericTank(int capacity, FluidStack... acceptableFluids) {
		super(capacity);
		this.filter = filter(acceptableFluids);
	}

	public GenericTank(int capacity, Fluid... acceptableFluids) {
		super(capacity);
		this.filter = filter(CollectionUtils.transform(acceptableFluids, FLUID_CONVERTER));
	}

	private static boolean isNeighbourTank(World world, BlockPos coord, EnumFacing dir) {
		TileEntity tile = BlockUtils.getTileInDirection(world, coord, dir);
		return tile instanceof IFluidHandler;
	}

	private static Set<EnumFacing> getSurroundingTanks(World world, BlockPos coord) {
		final Set<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);

		for (EnumFacing dir : EnumFacing.VALUES)
			if (isNeighbourTank(world, coord, dir)) result.add(dir);

		return result;
	}

	public FluidStack drain(FluidStack resource, boolean doDrain) {
		if (resource == null ||
				fluid == null ||
				fluid.isFluidEqual(resource)) return null;

		return drain(resource.amount, doDrain);
	}

	public int getSpace() {
		return getCapacity() - getFluidAmount();
	}

	@Override
	public int fill(FluidStack resource, boolean doFill) {
		if (resource == null || !filter.canAcceptFluid(resource)) return 0;
		return super.fill(resource, doFill);
	}

	public void updateNeighbours(World world, BlockPos coord, Set<EnumFacing> sides) {
		this.surroundingTanks = Lists.newArrayList(Sets.difference(getSurroundingTanks(world, coord), sides));
	}

	public void updateNeighbours(World world, BlockPos coord) {
		this.surroundingTanks = Lists.newArrayList(getSurroundingTanks(world, coord));
	}

	private static int tryFillNeighbour(FluidStack drainedFluid, EnumFacing side, TileEntity otherTank) {
		final FluidStack toFill = drainedFluid.copy();
		final EnumFacing fillSide = side.getOpposite();

		if (otherTank instanceof IFluidHandler) return ((IFluidHandler)otherTank).fill(fillSide, toFill, true);
		return 0;
	}

	public void distributeToSides(int amountPerTick, World world, BlockPos coord, Set<EnumFacing> sides) {
		if (world == null) return;

		if (getFluidAmount() <= 0) return;

		if (surroundingTanks.isEmpty()) return;

		FluidStack drainedFluid = drain(amountPerTick, false);

		if (drainedFluid != null && drainedFluid.amount > 0) {
			int startingAmount = drainedFluid.amount;
			Collections.shuffle(surroundingTanks);

			for (EnumFacing side : surroundingTanks) {
				if (drainedFluid.amount <= 0) break;

				TileEntity otherTank = BlockUtils.getTileInDirection(world, coord, side);
				if (otherTank != null) drainedFluid.amount -= tryFillNeighbour(drainedFluid, side, otherTank);
			}

			// return any remainder
			int distributed = startingAmount - drainedFluid.amount;
			if (distributed > 0) drain(distributed, true);
		}
	}

	public void fillFromSides(int maxAmount, World world, BlockPos coord) {
		fillFromSides(maxAmount, world, coord, null);
	}

	public void fillFromSides(int maxAmount, World world, BlockPos coord, Set<EnumFacing> sides) {
		if (world == null) return;

		int toDrain = Math.min(maxAmount, getSpace());
		if (toDrain <= 0) return;

		if (surroundingTanks.isEmpty()) return;

		Collections.shuffle(surroundingTanks);
		for (EnumFacing side : surroundingTanks) {
			if (toDrain <= 0) break;
			if (sides == null || sides.contains(side)) {
				toDrain -= fillInternal(world, coord, side, toDrain);
			}
		}
	}

	public int fillFromSide(World world, BlockPos coord, EnumFacing side) {
		int maxDrain = getSpace();
		if (maxDrain <= 0) return 0;

		return fillInternal(world, coord, side, maxDrain);
	}

	public int fillFromSide(int maxDrain, World world, BlockPos coord, EnumFacing side) {
		maxDrain = Math.max(maxDrain, getSpace());
		if (maxDrain <= 0) return 0;

		return fillInternal(world, coord, side, maxDrain);
	}

	private int fillInternal(World world, BlockPos coord, EnumFacing side, int maxDrain) {
		int drain = 0;
		final TileEntity otherTank = BlockUtils.getTileInDirection(world, coord, side);

		if (otherTank instanceof IFluidHandler) {
			final EnumFacing drainSide = side.getOpposite();
			final IFluidHandler handler = (IFluidHandler)otherTank;
			final FluidTankInfo[] infos = handler.getTankInfo(drainSide);

			if (infos == null) return 0;

			for (FluidTankInfo info : infos) {
				if (filter.canAcceptFluid(info.fluid)) {
					final FluidStack drained = handler.drain(drainSide, maxDrain, true);

					if (drained != null) {
						fill(drained, true);
						drain += drained.amount;
						maxDrain -= drained.amount;
						if (maxDrain <= 0) break;
					}
				}
			}
		}

		return drain;
	}

}
