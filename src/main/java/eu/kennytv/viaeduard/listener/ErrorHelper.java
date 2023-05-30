/*
 * Copyright (c) EngineHub and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package eu.kennytv.viaeduard.listener;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.kennytv.viaeduard.ViaEduardBot;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

// Loosely based on https://github.com/EngineHub/EngineHub-Bot/blob/master/src/main/java/org/enginehub/discord/module/errorHelper/ErrorHelper.java (see above copyright header)
public class ErrorHelper extends ListenerAdapter {

    private static final int MAX_IMAGE_BYTES = 1024 * 1024 * 5;
    private static final int MAX_FILE_BYTES = 1024 * 1024 * 5;
    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("OCR Task %d").build());
    private final List<ErrorEntry> errorMessages = new ArrayList<>();
    private final String tessDataPath = Path.of("tessdata").toAbsolutePath().toString();
    private final boolean enableImageScanning;
    private final ViaEduardBot bot;

    public ErrorHelper(final ViaEduardBot bot, final JsonObject object) {
        this.bot = bot;

        for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            final JsonObject errorObject = entry.getValue().getAsJsonObject();
            final ErrorEntry errorEntry = new ErrorEntry(
                    entry.getKey(),
                    errorObject.getAsJsonArray("match-text").asList().stream().map(JsonElement::getAsString).collect(Collectors.toList()),
                    errorObject.getAsJsonPrimitive("error-message").getAsString(),
                    errorObject.getAsJsonPrimitive("confidence").getAsInt(),
                    errorObject.getAsJsonArray("from").asList().stream().map(element -> ErrorContainer.valueOf(element.getAsString().toUpperCase(Locale.ROOT))).collect(Collectors.toList())
            );
            errorMessages.add(errorEntry);
        }

        enableImageScanning = object.getAsJsonPrimitive("enable-image-scanning").getAsBoolean();
    }

    @Override
    public void onMessageReceived(@Nonnull final MessageReceivedEvent event) {
        if (event.isWebhookMessage()) {
            return;
        }

        final long id = event.getAuthor().getIdLong();
        if (id == bot.getJda().getSelfUser().getIdLong()) {
            return;
        }

        final Message message = event.getMessage();
        final boolean sendDebug = message.isFromGuild();
        handle(message, message.getContentRaw(), ErrorContainer.TEXT, sendDebug);

        for (final Message.Attachment attachment : message.getAttachments()) {
            final String fileName = attachment.getFileName();
            if (fileName.endsWith(".txt") || fileName.endsWith(".log")) {
                if (attachment.getSize() > MAX_FILE_BYTES) {
                    continue;
                }

                final StringBuilder builder = new StringBuilder();
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(attachment.getProxy().download().get()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                } catch (final IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    continue;
                }

                handle(message, builder.toString(), ErrorContainer.FILE, sendDebug);
            } else if (enableImageScanning && attachment.isImage()) {
                if (attachment.getSize() > MAX_IMAGE_BYTES) {
                    continue;
                }

                executorService.execute(() -> readImage(attachment, message, sendDebug));
            }
        }
    }

    private void readImage(final Message.Attachment attachment, final Message message, final boolean sendDebug) {
        final BufferedImage image;
        try (final InputStream is = attachment.getProxy().download().get()) {
            image = ImageIO.read(is);
        } catch (final Exception e) {
            System.err.println("Error while reading image: " + attachment.getUrl());
            e.printStackTrace();
            return;
        }

        final String text;
        try {
            final Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            text = tesseract.doOCR(image);
        } catch (final TesseractException e) {
            System.err.println("Error while doing OCR on image: " + attachment.getUrl());
            e.printStackTrace();
            return;
        } finally {
            image.flush();
        }

        if (message.getAuthor().getIdLong() == 259465250716254210L && message.getChannel() instanceof PrivateChannel) {
            message.getChannel().sendMessage("[OCR Debug] " + text).queue();
        }

        handle(message, text, ErrorContainer.IMAGE, sendDebug);
    }

    private void handle(final Message message, final String messageContent, final ErrorContainer errorContainer, final boolean sendDebug) {
        final String cleanMessage = cleanString(messageContent);
        for (final ErrorEntry entry : errorMessages) {
            final Result result = entry.test(cleanMessage, errorContainer);
            if (!result.triggered()) {
                continue;
            }

            final String response = entry.response();
            message.getChannel().sendMessage(response + " " + message.getAuthor().getAsMention()).queue();

            if (sendDebug) {
                final String debugMessage = "Triggered " + entry.name() + " on " + message.getJumpUrl() + " (required confidence: " + entry.requiredConfidence() + ")"
                        + "\nPartial confidence: " + result.heighestPartialRatio()
                        + "\nWeighted confidence: " + result.heighestWeightedRatio();
                bot.getGuild().getChannelById(TextChannel.class, bot.getBotChannelId()).sendMessage(debugMessage).queue();
            }
        }
    }

    private static String cleanString(final String string) {
        return string.toLowerCase(Locale.ROOT)
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("\t", "")
                .replace("’", "'")
                .replace("‘", "'")
                .replace("“", "\"")
                .replace("”", "\"");
    }

    private static String getStringFromUrl0(final String url, final int tries) {
        final StringBuilder main = new StringBuilder();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                main.append(line);
            }
        } catch (final Throwable e) {
            System.err.println("Failed to load URL " + url + " (Tries " + tries + ')');
            e.printStackTrace();
            if (tries < 5) {
                return getStringFromUrl0(url, tries + 1);
            }
        }

        return main.toString();
    }

    public enum ErrorContainer {
        FILE,
        TEXT,
        IMAGE
    }

    public static class ErrorEntry {
        private static final double MIN_LENGTH = 0.8D;
        private final String name;
        private final List<String> cleanedTriggers;
        private final String response;
        private final int requiredConfidence;
        private final EnumSet<ErrorContainer> containers;

        ErrorEntry(final String name, final List<String> triggers, final String response, final int requiredConfidence, final Collection<ErrorContainer> containers) {
            this.name = name;
            this.cleanedTriggers = triggers.stream().map(ErrorHelper::cleanString).collect(Collectors.toList());
            this.response = response;
            this.requiredConfidence = requiredConfidence;
            this.containers = EnumSet.copyOf(containers);
        }

        Result test(final String error, final ErrorContainer errorContainer) {
            if (!containers.contains(errorContainer)) {
                return Result.NONE;
            }

            int heighestPartialRatio = 0;
            int heighestWeightedRatio = 0;
            for (final String cleanedTrigger : cleanedTriggers) {
                if (error.length() < cleanedTrigger.length() * MIN_LENGTH) {
                    return Result.NONE;
                }

                final int partialRatio = FuzzySearch.partialRatio(cleanedTrigger, error);
                final int weightedRatio = FuzzySearch.weightedRatio(cleanedTrigger, error);
                if (partialRatio < requiredConfidence || weightedRatio < requiredConfidence) {
                    return Result.NONE;
                }

                heighestPartialRatio = Math.max(heighestPartialRatio, partialRatio);
                heighestWeightedRatio = Math.max(heighestWeightedRatio, weightedRatio);
            }
            return new Result(true, heighestPartialRatio, heighestWeightedRatio);
        }

        public String name() {
            return name;
        }

        public String response() {
            return this.response;
        }

        public int requiredConfidence() {
            return requiredConfidence;
        }
    }

    public static final class Result {
        private static final Result NONE = new Result(false, 0, 0);
        private final boolean triggered;
        private final int heighestPartialRatio;
        private final int heighestWeightedRatio;

        Result(final boolean triggered, final int heighestPartialRatio, final int heighestWeightedRatio) {
            this.triggered = triggered;
            this.heighestPartialRatio = heighestPartialRatio;
            this.heighestWeightedRatio = heighestWeightedRatio;
        }

        public boolean triggered() {
            return triggered;
        }

        public int heighestPartialRatio() {
            return heighestPartialRatio;
        }

        public int heighestWeightedRatio() {
            return heighestWeightedRatio;
        }
    }
}