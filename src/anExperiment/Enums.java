package anExperiment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class Enums {

    public enum Model {
        m1, m2, m3
    }

    public enum Channel {
        c1, c2, c3
    }

    public enum ModelTarget {
        m1c1(Model.m1, Channel.c1),
        m1c2(Model.m1, Channel.c2);

        public final Model model;
        public final Channel channel;

        ModelTarget(Model model, Channel channel) {
            this.model = model;
            this.channel = channel;
        }

        // Which channels each model can be reached through. Built once at class-load.
        // EnumSet.add returns false if the pairing already exists, so this also
        // rejects any duplicate (model, channel) pair declared above.
        private static final Map<Model, EnumSet<Channel>> byModel = new EnumMap<>(Model.class);
        static {
            for (ModelTarget target : values()) {
                EnumSet<Channel> channels =
                        byModel.computeIfAbsent(target.model, m -> EnumSet.noneOf(Channel.class));
                if (!channels.add(target.channel)) {
                    throw new ExceptionInInitializerError(
                            "Duplicate model target pairing: " + target.model + " x " + target.channel);
                }
            }
        }

        /** Channels this model can be reached through (empty set if none). */
        public static EnumSet<Channel> channelsFor(Model model) {
            EnumSet<Channel> channels = byModel.get(model);
            return channels == null ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(channels);
        }
    }

    public static void main(String[] args) {
        for (ModelTarget target : ModelTarget.values()) {
            System.out.println(target + " -> model=" + target.model + ", channel=" + target.channel);
        }
        System.out.println();
        for (Model model : Model.values()) {
            System.out.println("channels for " + model + ": " + ModelTarget.channelsFor(model));
        }
    }
}