package eu.kennytv.viaeduard.util;

import org.jetbrains.annotations.NotNull;

public final class Version implements Comparable<Version> {

    private final int[] parts;
    private final String version;
    private final String tag;

    public Version(final String version) {
        final int index = version.indexOf('-');
        final String[] split = (index == -1 ? version : version.substring(0, index)).split("\\.");
        parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            parts[i] = Integer.parseInt(split[i]);
        }

        this.version = version;
        tag = index != -1 ? version.substring(index + 1) : "";
    }

    /**
     * Compare two versions.
     *
     * @param version version to compare to
     * @return 0 if they are the same, 1 if this instance is newer, -1 if older
     */
    @Override
    public int compareTo(@NotNull final Version version) {
        if (version == null || version.toString() == null) return 0;
        final int max = Math.max(this.parts.length, version.parts.length);
        for (int i = 0; i < max; i++) {
            final int partA = i < this.parts.length ? this.parts[i] : 0;
            final int partB = i < version.parts.length ? version.parts[i] : 0;
            if (partA < partB) return -1;
            if (partA > partB) return 1;
        }

        if (this.tag.isEmpty() && !version.tag.isEmpty()) {
            return 1;
        }
        if (!this.tag.isEmpty() && version.tag.isEmpty()) {
            return -1;
        }
        return 0;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return version;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Version)) return false;
        final Version other = (Version) o;
        return version.equals(other.version);
    }
}