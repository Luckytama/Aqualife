package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker extends Thread {

    private static final int THREAD_POOL_SIZE = 6;

    private boolean stopThisShit = false;

    public static ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    private volatile ClientCollection<InetSocketAddress> clients;

    private final Endpoint endpoint;

    public Broker() {
        this.endpoint = new Endpoint(4711);
        this.clients = new ClientCollection();
    }

    private class BrokerTask implements Runnable {

        private Message msg;

        BrokerTask(final Message msg) {
            this.msg = msg;
        }

        @Override public void run() {
            if (msg.getPayload() instanceof RegisterRequest) {
                register(msg.getSender());
            } else if (msg.getPayload() instanceof DeregisterRequest) {
                System.out.println("DeregisterRequest from " + ((DeregisterRequest) msg.getPayload()).getId());
                derregister(msg.getSender());
            } else if (msg.getPayload() instanceof HandoffRequest) {
                System.out.println("HandoffRequest from " + ((HandoffRequest) msg.getPayload()).getFish().getId());
                handoffFish(((HandoffRequest) msg.getPayload()).getFish(), msg.getSender());
            } else if (msg.getPayload() instanceof PoisonPill) {
                executor.shutdown();
            } else {
                throw new IllegalArgumentException("Received unknown message type");
            }
        }
    }

    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();

    }

    public void broker() {
        Thread stopThread = new Thread(new Runnable() {

            @Override public void run() {
                JOptionPane.showMessageDialog(null, "Stop?");
                stopThisShit = true;
            }
        });
        stopThread.start();
        while (!stopThisShit) {
            Message msg = endpoint.nonBlockingReceive();
            if(null != msg) {
                System.out.println("received message");
                executor.execute(new BrokerTask(msg));
            }
        }
        System.out.println("Stop signal revieved");
        executor.shutdown();
    }

    private synchronized void register(final InetSocketAddress sender) {
        clients.add("tank" + clients.size(), sender);
        int indexOfClient = clients.size()-1;
        endpoint.send(sender, new NeighborUpdate(clients.getLeftNeighorOf(indexOfClient), clients.getRightNeighorOf(indexOfClient)));
        if(1 < clients.size()) {
            endpoint.send(clients.getClient(indexOfClient-1), new NeighborUpdate(null, sender));
            endpoint.send(clients.getClient(0), new NeighborUpdate(sender, null));
        }
        endpoint.send(sender, new RegisterResponse("tank" + clients.size()));
    }

    private synchronized void derregister(final InetSocketAddress sender) {
        final int index = clients.indexOf(sender);
        clients.remove(clients.indexOf(sender));
        if (1 == clients.size()) {
            sendNeighborUpdates(0);
        } else if (1 < clients.size()) {
            sendNeighborUpdates(index-1);
            sendNeighborUpdates(index);
        }
    }

    private void handoffFish(final FishModel fish, final InetSocketAddress sender) {
        ReadWriteLock locker = new ReentrantReadWriteLock();
        locker.readLock().lock();
        if (2 > clients.size()) {
            locker.readLock().unlock();
            locker.writeLock().lock();
            endpoint.send(sender, new HandoffRequest(fish));
            locker.writeLock().unlock();
            return;
        }
        if (Direction.LEFT == fish.getDirection()) {
            locker.readLock().unlock();
            locker.writeLock().lock();
            endpoint.send(clients.getLeftNeighorOf(clients.indexOf(sender)), new HandoffRequest(fish));
            locker.writeLock().unlock();
        } else {
            locker.readLock().unlock();
            locker.writeLock().lock();
            endpoint.send(clients.getRightNeighorOf(clients.indexOf(sender)), new HandoffRequest(fish));
            locker.writeLock().unlock();
        }
    }

    private void sendNeighborUpdates(int index) {
        endpoint.send(clients.getClient(index), new NeighborUpdate(clients.getLeftNeighorOf(index), clients.getRightNeighorOf(index)));
    }
}
