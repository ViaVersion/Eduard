package com.viaversion.eduard.util;

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
        "Dangerous"
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
        "Zebra"
    };

    private static final Random random = new Random();

    public static String randomName() {
        return adjectives[random.nextInt(adjectives.length)] + animals[random.nextInt(animals.length)];
    }
}