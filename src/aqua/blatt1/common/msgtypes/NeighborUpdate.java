package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;

public final class NeighborUpdate implements Serializable {
    public final InetSocketAddress leftNeighborAdress;
    public final InetSocketAddress rightNeighborAdress;

    public NeighborUpdate(InetSocketAddress leftNeighborAdress, InetSocketAddress rightNeighborAdress) {
        this.leftNeighborAdress = leftNeighborAdress;
        this.rightNeighborAdress = rightNeighborAdress;
    }
}
