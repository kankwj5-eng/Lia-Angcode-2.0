package com.kankwj.angcode.connectors.shizuku;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class AngCodePrivilegedService extends IAngCodePrivilegedService.Stub {

    private static final int MAX_OUTPUT_BYTES = 512 * 1024;
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of(
            "id", "pm", "cmd", "am", "monkey", "logcat", "dumpsys", "settings"
    );

    public AngCodePrivilegedService() {}

    public AngCodePrivilegedService(Context context) {}

    @Override
    public String run(String[] command, int timeoutMs) {
        if (command == null || command.length == 0) {
            return "ERROR: comando vacío";
        }

        String executable = command[0];
        if (!ALLOWED_EXECUTABLES.contains(executable)) {
            return "ERROR: ejecutable no permitido: " + executable;
        }

        int timeout = Math.max(1_000, Math.min(timeoutMs, 120_000));
        return runProcess(command, timeout, null);
    }

    @Override
    public String installApk(ParcelFileDescriptor apk, long size, boolean replaceExisting) {
        if (apk == null) return "ERROR: descriptor APK nulo";
        if (size <= 0 || size > 4L * 1024L * 1024L * 1024L) {
            closeQuietly(apk);
            return "ERROR: tamaño APK inválido";
        }

        String[] command = replaceExisting
                ? new String[]{"pm", "install", "-r", "-S", Long.toString(size), "-"}
                : new String[]{"pm", "install", "-S", Long.toString(size), "-"};

        try (ParcelFileDescriptor descriptor = apk;
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            return runProcess(command, 180_000, input);
        } catch (Throwable error) {
            return "ERROR: " + error;
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static String runProcess(
            String[] command,
            int timeoutMs,
            InputStream stdin
    ) {
        Process process = null;
        Thread outputThread = null;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            final Process active = process;
            outputThread = new Thread(() -> copyLimited(active.getInputStream(), output));
            outputThread.setDaemon(true);
            outputThread.start();

            if (stdin != null) {
                try (java.io.OutputStream processInput = process.getOutputStream()) {
                    byte[] buffer = new byte[1024 * 1024];
                    while (true) {
                        int read = stdin.read(buffer);
                        if (read <= 0) break;
                        processInput.write(buffer, 0, read);
                    }
                }
            } else {
                process.getOutputStream().close();
            }

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "ERROR: timeout " + timeoutMs + "ms";
            }

            if (outputThread != null) outputThread.join(2_000);

            String text = output.toString(java.nio.charset.StandardCharsets.UTF_8);
            return "exit=" + process.exitValue() + "\n" + text.trim();
        } catch (Throwable error) {
            if (process != null) process.destroyForcibly();
            return "ERROR: " + error + "\ncommand=" + Arrays.toString(command);
        }
    }

    private static void copyLimited(InputStream input, ByteArrayOutputStream output) {
        try (InputStream source = input) {
            byte[] buffer = new byte[8_192];
            int total = 0;
            while (total < MAX_OUTPUT_BYTES) {
                int read = source.read(buffer, 0, Math.min(buffer.length, MAX_OUTPUT_BYTES - total));
                if (read <= 0) break;
                synchronized (output) {
                    output.write(buffer, 0, read);
                }
                total += read;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        try {
            descriptor.close();
        } catch (Throwable ignored) {
        }
    }
}
