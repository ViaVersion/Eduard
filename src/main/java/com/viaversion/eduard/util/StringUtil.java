package com.viaversion.eduard.util;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;

import java.util.Random;

// Thanks to https://github.com/e-im/pencil/blob/main/src/main/java/me/sulu/pencil/util/StringUtil.java
public final class StringUtil {

    private static final String[] adjectives = {
            "Fast",
            "Quick",
            "Slow",
            "Bright",
            "Noisy",
            "Loud",
            "Quiet",
            "Brave",
            "Sad",
            "Proud",
            "Happy",
            "Comfortable",
            "Clever",
            "Interesting",
            "Funny",
            "Kind",
            "Polite",
            "Fair",
            "Careful",
            "Safe",
            "Dangerous",
            "Curious",
            "Sneaky",
            "Gentle",
            "Bold",
            "Lazy",
            "Energetic",
            "Playful",
            "Fierce",
            "Mighty",
            "Tiny",
            "Giant",
            "Grumpy",
            "Shy",
            "Witty",
            "Charming",
            "Angry",
            "Sleepy",
            "Loyal",
            "Elegant",
            "Swift"
    };
    private static final String[] animals = {
            "Aardvark",
            "Alligator",
            "Ant",
            "Crab",
            "Cricket",
            "Crow",
            "Deer",
            "Dog",
            "Flamingo",
            "Frog",
            "Fox",
            "Herring",
            "Llama",
            "Mongoose",
            "Quail",
            "Tiger",
            "Weasel",
            "Wolf",
            "Yak",
            "Zebra",
            "Bat",
            "Cheetah",
            "Dolphin",
            "Eagle",
            "Elephant",
            "Giraffe",
            "Hamster",
            "Iguana",
            "Jaguar",
            "Kangaroo",
            "Leopard",
            "Moose",
            "Narwhal",
            "Ocelot",
            "Ostrich",
            "Panda",
            "Penguin",
            "Raccoon",
            "Seal",
            "Walrus"
    };

    private static final Random random = new Random();

    public static String randomName(final TextChannel channel) {
        final String name = adjectives[random.nextInt(adjectives.length)] + animals[random.nextInt(animals.length)];
        for (final ThreadChannel threadChannel : channel.getThreadChannels()) {
            if (threadChannel.getName().equalsIgnoreCase(name)) {
                return randomName(channel);
            }
        }

        return name;
    }
}