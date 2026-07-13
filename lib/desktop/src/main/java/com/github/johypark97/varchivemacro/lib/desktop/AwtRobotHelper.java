package com.github.johypark97.varchivemacro.lib.desktop;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

public class AwtRobotHelper {
    
    private static final boolean IS_WAYLAND = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));

    public static void sleepLeast(long timeout) throws InterruptedException {
        boolean interrupted = false;
        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(timeout);
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            throw new InterruptedException();
        }
    }

    public static void tabKey(Robot robot, long duration, int keyCode, int... modifier)
            throws InterruptedException {
        boolean interrupted = false;
        Arrays.stream(modifier).forEach(robot::keyPress);
        robot.keyPress(keyCode);

        try {
            sleepLeast(duration);
        } catch (InterruptedException e) {
            interrupted = true;
        }

        robot.keyRelease(keyCode);
        Arrays.stream(modifier).forEach(robot::keyRelease);

        try {
            sleepLeast(duration);
        } catch (InterruptedException e) {
            interrupted = true;
        }

        if (interrupted) {
            throw new InterruptedException();
        }
    }

    public static BufferedImage captureScreenshot(Robot robot) {
        // 1. Get the bounds of the primary (first) screen explicitly
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        
        // Default to the first screen device bounds
        Rectangle primaryScreenBounds = screens.length > 0 
                ? screens[0].getDefaultConfiguration().getBounds()
                : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        if (IS_WAYLAND) {
            BufferedImage nativeCapture = captureWaylandNative(primaryScreenBounds);
            if (nativeCapture != null) {
                return nativeCapture;
            }
        }

        // Default X11 Fallback targeting only the primary screen bounds
        MultiResolutionImage multiResolutionImage =
                robot.createMultiResolutionScreenCapture(primaryScreenBounds);
        List<Image> variantList = multiResolutionImage.getResolutionVariants();
        Image image = variantList.get(variantList.size() - 1);

        BufferedImage bufferedImage = new BufferedImage(image.getWidth(null), image.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        Graphics graphics = bufferedImage.getGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        return bufferedImage;
    }

    /**
     * Captures only the target screen area on Wayland
     */
    private static BufferedImage captureWaylandNative(Rectangle bounds) {
        try {
            File tempFile = File.createTempFile("varchive_cap", ".png");
            tempFile.deleteOnExit();

            ProcessBuilder pb;
            if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") != null) {
                // For grim, passing the specific geometry isolates the first monitor
                String geometry = String.format("%d,%d %dx%d", 
                        bounds.x, bounds.y, bounds.width, bounds.height);
                pb = new ProcessBuilder("grim", "-g", geometry, tempFile.getAbsolutePath());
            } else {
                // For KDE/Spectacle, target the primary monitor index
                pb = new ProcessBuilder("spectacle", "-b", "-n", "-m", "-o", tempFile.getAbsolutePath());
            }

            if (pb.start().waitFor() == 0) {
                return ImageIO.read(tempFile);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Wayland specific screen capture failed: " + e.getMessage());
        }
        return null;
    }
}