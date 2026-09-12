package anExperiment;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class Enums {

    public enum Model {
        m1, m2, m3
    }

    public enum Source {
        s1, s2, s3
    }

    public enum Channel {
        c1(Source.s1), c2(Source.s2), c3(Source.s3);

        private final Source source;

        Channel(Source source) {
            this.source = source;
        }

        public Source source() {
            return source;
        }
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

        // The real data shows Source depends only on the channel. If that ever stops
        // being true — a model that changes provenance within the same channel — the
        // derivation would move to a Map<Pair<Model,Channel>, Source> lookup instead.
        // For now, it remains channel-keyed.
        public Source source() {
            return channel.source();
        }

        // Which channels each model can be reached through. Built once at class-load.
        // EnumSet.add returns false if the pairing already exists, so this also
        // rejects any duplicate (model, channel) pair declared above.
        private static final Map<Model, EnumSet<Channel>> byModel = new EnumMap<>(Model.class);
        private static final Map<Channel, EnumSet<Model>> byChannel = new EnumMap<>(Channel.class);
        static {
            for (ModelTarget target : values()) {
                EnumSet<Channel> channels =
                        byModel.computeIfAbsent(target.model, m -> EnumSet.noneOf(Channel.class));
                if (!channels.add(target.channel)) {
                    throw new ExceptionInInitializerError(
                            "Duplicate model target pairing: " + target.model + " x " + target.channel);
                }
                
                EnumSet<Model> models =
                        byChannel.computeIfAbsent(target.channel, c -> EnumSet.noneOf(Model.class));
                models.add(target.model);
            }
        }

        /** Channels this model can be reached through (empty set if none). */
        public static EnumSet<Channel> channelsFor(Model model) {
            EnumSet<Channel> channels = byModel.get(model);
            return channels == null ? EnumSet.noneOf(Channel.class) : EnumSet.copyOf(channels);
        }

        /** Models this channel carries (empty set if none). */
        public static EnumSet<Model> modelsFor(Channel channel) {
            EnumSet<Model> models = byChannel.get(channel);
            return models == null ? EnumSet.noneOf(Model.class) : EnumSet.copyOf(models);
        }
    }

    public static void main(String[] args) {
        for (ModelTarget target : ModelTarget.values()) {
            System.out.println(target + " -> model=" + target.model + ", channel=" + target.channel + ", derived source=" + target.source());
        }
        System.out.println();
        for (Model model : Model.values()) {
            System.out.println("channels for " + model + ": " + ModelTarget.channelsFor(model));
        }
        System.out.println();
        for (Channel channel : Channel.values()) {
            System.out.println("models for " + channel + ": " + ModelTarget.modelsFor(channel));
        }
    }
}
