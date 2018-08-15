/*
 * Copyright 2017 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.opengl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.context.Context;
import org.terasology.engine.paths.PathManager;
import org.terasology.engine.subsystem.common.ThreadManager;
import org.terasology.game.Game;
import org.terasology.persistence.internal.GamePreviewImageProvider;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs.FINAL_BUFFER;

// TODO: Future work should not only "think" in terms of a DAG-like rendering pipeline
// TODO: but actually implement one, see https://github.com/MovingBlocks/Terasology/issues/1741
public class ScreenGrabber {
    private static final Logger logger = LoggerFactory.getLogger(ScreenGrabber.class);

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyMMddHHmmss");
    private static final String SCREENSHOT_FILENAME_PATTERN = "Terasology-%s-%dx%d.%s";

    private RenderingConfig renderingConfig;
    private ThreadManager threadManager;
    private float currentExposure;
    private boolean isTakingScreenshot;
    private DisplayResolutionDependentFBOs displayResolutionDependentFBOs;
    private boolean savingGamePreview;
    private boolean savingScreenshot;

    /**
     * @param context
     */
    public ScreenGrabber(Context context) {
        threadManager = CoreRegistry.get(ThreadManager.class);
        renderingConfig = context.get(Config.class).getRendering();
    }

    /**
     * Returns the current exposure value (set in downsampleSceneAndUpdateExposure()).
     *
     * @return a float representing the current exposure value.
     */
    public float getExposure() {
        return currentExposure;
    }

    // TODO: Remove this method, temporarily here for DownSampleSceneAndUpdateExposure
    public void setExposure(float exposure) {
        this.currentExposure = exposure;
    }

    /**
     * Triggers a screenshot.
     *
     * Notice that this method just starts the process: screenshot data is captured and written to file
     * as soon as possible but not necessarily immediately after the trigger.
     */
    public void takeScreenshot() {
        savingScreenshot = true;
        isTakingScreenshot = true;
    }

    /**
     * Schedules the saving of screenshot data to file.
     *
     * Screenshot data from the GPU is obtained as soon as this method executes. However, the data is only scheduled
     * to be written to file, by submitting a task to the ThreadManager. The task is then executed as soon as possible
     * but not necessarily immediately.
     *
     * The file is then saved in the designated screenshot folder, or if it's a game preview image, it'll be placed in the save game folder.
     *
     * If no screenshot data is available an error is logged and the method returns doing nothing.
     */
    public void saveScreenshot() {
        // Since ScreenGrabber is initialized before DisplayResolutionDependentFBOs (because the latter contains a reference to the former)
        // on first call on saveScreenshot() displayResolutionDependentFBOs will be null.
        if (displayResolutionDependentFBOs == null) {
            displayResolutionDependentFBOs = CoreRegistry.get(DisplayResolutionDependentFBOs.class);
        }

        FBO sceneFinalFbo = displayResolutionDependentFBOs.get(FINAL_BUFFER);

        final ByteBuffer buffer = sceneFinalFbo.getColorBufferRawData();
        if (buffer == null) {
            logger.error("No screenshot data available. No screenshot will be saved.");
            return;
        }

        int width = sceneFinalFbo.width();
        int height = sceneFinalFbo.height();

        threadManager.submitTask("Write screenshot", () -> saveScreenshotTask(buffer, width, height));
        isTakingScreenshot = false;
    }

    /**
     * Saves given screenshot data to file.
     *
     * The file is then saved in the designated screenshot folder with a filename in the form:
     *
     *     Terasology-[yyMMddHHmmss]-[width]x[height].[format]
     */
    private void saveScreenshotTask(ByteBuffer buffer, int width, int height) {
        final String format = renderingConfig.getScreenshotFormat();
        final BufferedImage image = convertByteBufferToBufferedImage(buffer, width, height);
        if (savingGamePreview) {
            writeImageToFile(image, getGamePreviewPath(), format);
            savingGamePreview = false;
        }
        if (savingScreenshot) {
            writeImageToFile(image, getScreenshotPath(width, height, format), format);
            savingScreenshot = false;
        }
    }

    private Path getGamePreviewPath() {
        final Game game = CoreRegistry.get(Game.class);
        if (game != null) {
            final Path saveDirPath = PathManager.getInstance().getSavePath(game.getName());
            return GamePreviewImageProvider.getNextGamePreviewImagePath(saveDirPath);
        } else {
            logger.warn("Can't schedule saving game preview image, because can't find Game instance");
            return null;
        }
    }

    private void writeImageToFile(BufferedImage image, Path path, String format) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            ImageIO.write(image, format, out);
            logger.info("Screenshot saved to {}! ", path);
        } catch (IOException e) {
            logger.warn("Failed to save screenshot!", e);
        }
    }

    /**
     * Transforms ByteBuffer into BufferedImage with specified width and height.
     */
    private BufferedImage convertByteBufferToBufferedImage(ByteBuffer buffer, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int i = (x + width * y) * 4;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                image.setRGB(x, height - (y + 1), (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private Path getScreenshotPath(final int width, final int height, final String format) {
        final String fileName = String.format(
          SCREENSHOT_FILENAME_PATTERN, SIMPLE_DATE_FORMAT.format(new Date()), width, height, format);
        return PathManager.getInstance().getScreenshotPath().resolve(fileName);
    }

    /**
     * Returns true if the rendering engine is in the process of taking a screenshot.
     * Returns false if a screenshot is not being taken.
     *
     * @return true if a screenshot is being taken, false otherwise
     */
    public boolean isTakingScreenshot() {
        return isTakingScreenshot;
    }

    /**
     * Schedules the saving of game preview screenshot.
     */
    public void takeGamePreview() {
        savingGamePreview = true;
        saveScreenshot();
    }
}
