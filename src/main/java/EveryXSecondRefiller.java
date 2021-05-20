import jdk.nashorn.internal.objects.annotations.Constructor;
import lombok.SneakyThrows;

import java.util.concurrent.Semaphore;

class EveryXSecondRefiller extends Thread {
    private volatile boolean done = false;
    private static Semaphore maximumRequestQuotaSemaphore;
    private int maxRequestQuota;
    private int timeToRestore;

    public EveryXSecondRefiller(int maxRequestQuota, int timeToRestore) {
        this.maxRequestQuota = maxRequestQuota;
        this.timeToRestore = timeToRestore;
    }

    public Semaphore getSemaphore() {
        maximumRequestQuotaSemaphore = new Semaphore(this.maxRequestQuota);
        return maximumRequestQuotaSemaphore;
    }

    @SneakyThrows
    @Override
    public void run() {
        while (!done()) {
            int availablePermits = maximumRequestQuotaSemaphore.availablePermits();
            if (availablePermits == maxRequestQuota) {
                sleep(timeToRestore);
                continue;
            }
            maximumRequestQuotaSemaphore.release(1);
            sleep(timeToRestore);
        }
    }


    private boolean done() {
        return done;
    }

    void close() {
        done = true;
    }

}