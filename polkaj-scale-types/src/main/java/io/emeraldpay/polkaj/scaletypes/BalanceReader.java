package io.emeraldpay.polkaj.scaletypes;

import java.math.BigInteger;

import io.emeraldpay.polkaj.scale.ScaleCodecReader;
import io.emeraldpay.polkaj.scale.ScaleReader;
import io.emeraldpay.polkaj.ss58.SS58Type;
import io.emeraldpay.polkaj.types.DotAmount;
import io.emeraldpay.polkaj.types.Units;

/**
 * Decode balance encoded as uint128
 */
public class BalanceReader implements ScaleReader<DotAmount> {

    private final Units units;
    private final SS58Type.Network network;

    /**
     * Create a default balance reader for the Polkadot network, i.e. DOT.
     */
    public BalanceReader() {
        this.units = DotAmount.Polkadots;
        this.network = SS58Type.Network.POLKADOT;
    }

    /**
     * <p>Create a balance reader for the provided network.</p>
     * <p>Transforms the read balance to the unit matching the network (DOT, KSM, WND).</p>
     *
     * @param network the SS58 network whose unit to consider when reading accounts
     */
    public BalanceReader(SS58Type.Network network) {
        this.units = DotAmount.getUnitsForNetwork(network);
        this.network = network;
    }

    @Override
    public DotAmount read(ScaleCodecReader rdr) {
        BigInteger value = network == SS58Type.Network.BITTENSOR ? rdr.readUInt64() : rdr.readUint128();
        return new DotAmount(value, units);
    }
}
