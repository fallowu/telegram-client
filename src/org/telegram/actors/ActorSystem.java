package org.telegram.actors;

import org.telegram.actors.dispatch.RunnableDispatcher;
import org.telegram.actors.queue.ActorDispatcher;
import org.telegram.actors.queue.ActorMessage;
import org.telegram.actors.queue.ActorMessageQueue;

/**
 * Created by ex3ndr on 17.03.14.
 */
public class ActorSystem {

    private static final String TAG = "ActorSystem";

    // High performance singleton
    private static class DispatcherHolder {
        public static final RunnableDispatcher HOLDER_INSTANCE = new RunnableDispatcher();
    }

    private static RunnableDispatcher getThreadDispatcher() {
        return DispatcherHolder.HOLDER_INSTANCE;
    }

    private ThreadHolder[] holders;
    private int holdersCount;

    public ActorSystem() {
        holders = new ThreadHolder[16];
        holdersCount = 0;
    }

    private void checkHolders() {
        if (holdersCount == holders.length) {
            ThreadHolder[] nHolders = new ThreadHolder[holders.length + 16];
            for (int i = 0; i < holders.length; i++) {
                nHolders[i] = holders[i];
            }
            holders = nHolders;
        }
    }

    private void checkThread(final int id) {
        if (!holders[id].isCreated) {
            // Logger.d(TAG, "Creating thread " + holders[id].name);
            holders[id].isCreated = true;
            getThreadDispatcher().postAction(new Runnable() {
                @Override
                public void run() {
                    holders[id].actorThread = new ActorDispatcher(holders[id].threadPriority, holders[id].queue);
                    // Logger.d(TAG, "Thread " + holders[id].name + " created");
                }
            });
        }
    }

    public void addThread(String name) {
        addThread(name, Thread.NORM_PRIORITY);
    }

    public void addThread(String name, int priority) {
        checkHolders();
        holders[holdersCount++] = new ThreadHolder(name, priority);
    }

    public int getThreadId(String name) {
        for (int i = 0; i < holdersCount; i++) {
            if (holders[i].name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public void sendMessage(final int threadId, ActorMessage message) {
        sendMessage(threadId, message, 0);
    }

    public void sendMessage(final int threadId, ActorMessage message, long delay) {
        if (threadId < 0 || threadId >= holdersCount) {
            return;
        }
        // Logger.d(TAG, "Sending message " + message.getMessage() + " to " + message.getActor().getName());
        ActorMessageDesc desc = message.getActor().findDesc(message.getMessage());
        if (desc.isSingleShot()) {
            holders[threadId].queue.postToQueueUniq(message, ActorTime.currentTime() + delay);
        } else {
            holders[threadId].queue.putToQueue(message, ActorTime.currentTime() + delay);
        }
        checkThread(threadId);
    }

    public void onUnhandledMessage(Actor actor, String name, Object[] args, ActorReference reference) {
    }

    public void close() {
        for (ThreadHolder holder : holders) {
            if (holder.actorThread != null) {
                holder.actorThread.close();
            }
        }
    }

    private class ThreadHolder {
        public String name;
        public int threadPriority;
        public ActorMessageQueue queue;

        public ActorDispatcher actorThread;
        public boolean isCreated = false;

        private ThreadHolder(String name, int threadPriority) {
            this.name = name;
            this.threadPriority = threadPriority;
            this.queue = new ActorMessageQueue();
        }
    }
}