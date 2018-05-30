package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.Observable;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.NeighborUpdate;

public class TankModel extends Observable implements Iterable<FishModel> {

    public enum recordingMode {
        IDLE, LEFT, RIGTH, BOTH
    }

    public static final int WIDTH = 600;

    public static final int HEIGHT = 350;

    protected static final int MAX_FISHIES = 5;

    protected static final Random rand = new Random();

    protected volatile String id;

    protected final Set<FishModel> fishies;

    protected int fishCounter = 0;

    protected final ClientCommunicator.ClientForwarder forwarder;
    
    protected final ClientCommunicator.ClientReceiver reciever;

    protected InetSocketAddress leftNeighbor;

    protected InetSocketAddress rightNeighbor;

    public recordingMode recordingState;

    public int state;

    public TankModel(ClientCommunicator.ClientForwarder forwarder) {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.forwarder = forwarder;
        this.recordingState = recordingMode.IDLE;
        reciever = null;
    }

    synchronized void onRegistration(String id) {
        this.id = id;
        newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish =
                new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y, rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
        }
    }

    synchronized void receiveFish(FishModel fish) {
        fish.setToStart();
        fishies.add(fish);
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() {
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge())
                forwarder.handOff(fish);

            if (fish.disappears())
                it.remove();
        }
    }

    private synchronized void update() {
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() {
        forwarder.register();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() {
        forwarder.deregister(id);
    }

    public void updateNeighbors(NeighborUpdate msg) {
        this.leftNeighbor = msg.leftNeighborAdress;
        this.rightNeighbor = msg.rightNeighborAdress;
    }

    public void initiateSnapshot() {
        // safe lokal State
        updateState();
        // record all Input channels
        recordEntry(leftNeighbor);
        recordEntry(rightNeighbor);
        // send marker
        sendMarker(leftNeighbor);
        sendMarker(rightNeighbor);
    }

    private void recordEntry(InetSocketAddress client) {
        
    }

    private void sendMarker(InetSocketAddress client) {

    }

    private void updateState() {
        state = fishies.size();
    }
}