package chatmap.util;

public final class Locks {

    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    public static <T, E extends Exception> T locked(Object lock, Work<T, E> work) throws E {
        synchronized (lock) {
            return work.run();
        }
    }
    
    private Locks() {}
}
