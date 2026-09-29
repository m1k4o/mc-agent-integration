package com.mcai.client;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First-launch auto-install of the {@code mcai} terminal command.
 * The CLI script is bundled inside this jar ({@code /mcai-bin/mcai},
 * copied at build time from {@code bin/mcai}) and laid down onto the
 * user's PC so no manual install step is needed:
 * <ol>
 *   <li>{@code <gameDir>/mcai/bin/mcai} — always, every OS.</li>
 *   <li>{@code ~/.local/bin/mcai} — best effort on Linux/macOS.</li>
 * </ol>
 * Version-aware: existing files are only overwritten when missing, or when
 * they carry our marker and a different {@code VERSION}. A foreign file that
 * happens to be named {@code mcai} is never touched.
 */
public final class CliInstaller {
	private static final String MARKER = "mc-agent-integration";
	private static final Pattern VERSION_RE = Pattern.compile("(?m)^VERSION\\s*=\\s*\"([^\"]+)\"");
	private static boolean freshInstall = false;
	private static String installedPath = "";

	private CliInstaller() {}

	public static boolean freshInstall() { return freshInstall; }
	public static String installedPath() { return installedPath; }

	public static void install(Path gameDir) {
		String bundled;
		try {
			bundled = readBundledCli();
		} catch (Exception e) {
			com.mcai.McAgentIntegration.LOGGER.warn("[mcai] CLI bundle missing from jar: {}", e.toString());
			return;
		}
		if (bundled == null || !bundled.contains(MARKER)) {
			com.mcai.McAgentIntegration.LOGGER.warn("[mcai] CLI bundle failed marker check, skipping auto-install");
			return;
		}
		String bundledVersion = parseVersion(bundled);

		// 1. always stage next to the file bridge
		try {
			Path staged = gameDir.resolve("mcai").resolve("bin").resolve("mcai");
			if (writeIfNeeded(staged, bundled, bundledVersion)) {
				installedPath = staged.toString();
				freshInstall = true;
			}
		} catch (Exception e) {
			com.mcai.McAgentIntegration.LOGGER.warn("[mcai] could not stage CLI in gameDir: {}", e.toString());
		}

		// 2. best effort: user bin dir on unix-likes
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			boolean unix = os.contains("linux") || os.contains("mac") || os.contains("nix") || os.contains("nux");
			if (!unix) return;
			String home = System.getProperty("user.home", "");
			if (home.isEmpty()) return;
			Path target = Path.of(home).resolve(".local").resolve("bin").resolve("mcai");
			if (writeIfNeeded(target, bundled, bundledVersion)) {
				installedPath = target.toString();
				freshInstall = true;
				com.mcai.McAgentIntegration.LOGGER.info("[mcai] terminal command installed at {} (version {})",
					target, bundledVersion == null ? "?" : bundledVersion);
			}
		} catch (Exception e) {
			com.mcai.McAgentIntegration.LOGGER.warn("[mcai] could not install CLI to ~/.local/bin: {}", e.toString());
		}

		if (freshInstall) {
			com.mcai.McAgentIntegration.LOGGER.info(
				"[mcai] first launch: terminal command ready at {}. Ensure ~/.local/bin is on PATH, then run `mcai`.",
				installedPath.isEmpty() ? "<gameDir>/mcai/bin/mcai" : installedPath);
		}
	}

	private static String readBundledCli() throws Exception {
		try (InputStream in = CliInstaller.class.getResourceAsStream("/mcai-bin/mcai")) {
			if (in == null) return null;
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String parseVersion(String text) {
		Matcher m = VERSION_RE.matcher(text);
		return m.find() ? m.group(1) : null;
	}

	/**
	 * @return true if the file was (re)written.
	 */
	private static boolean writeIfNeeded(Path target, String bundled, String bundledVersion) throws Exception {
		if (Files.exists(target)) {
			String existing;
			try {
				byte[] bytes = Files.readAllBytes(target);
				if (bytes.length > 65536) return false;
				existing = new String(bytes, StandardCharsets.UTF_8);
			} catch (Exception e) {
				return false;
			}
			if (!existing.contains(MARKER)) {
				com.mcai.McAgentIntegration.LOGGER.info(
					"[mcai] {} exists and is not ours, leaving it alone", target);
				return false;
			}
			String existingVersion = parseVersion(existing);
			if (bundledVersion != null && bundledVersion.equals(existingVersion)) return false;
		} else {
			try {
				Files.createDirectories(target.getParent());
			} catch (Exception e) {
				return false;
			}
		}
		Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
		Files.writeString(tmp, bundled, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));
		} catch (UnsupportedOperationException ignored) {
			// Windows: no POSIX perms; the .py-less shebang won't execute there anyway.
			target.toFile().setExecutable(true, false);
		} catch (Exception ignored) {}
		return true;
	}
}
