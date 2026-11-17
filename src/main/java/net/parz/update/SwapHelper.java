package net.parz.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Runs in its own small Java process after Minecraft closed (see ModUpdater): waits until the old mod jar is
 * no longer locked, deletes it and moves the new jar in its place. Only uses the Java standard library.
 *
 * Arguments: old jar, new file (.new), final jar, temp folder of this helper (deleted at the end).
 */
public final class SwapHelper {
	private SwapHelper() {
	}

	public static void main(String[] args) throws Exception {
		Path oldJar = Path.of(args[0]);
		Path newFile = Path.of(args[1]);
		Path finalJar = Path.of(args[2]);
		Path tempDir = Path.of(args[3]);
		for (int i = 0; i < 90; i++) {
			Thread.sleep(2000);
			try {
				Files.deleteIfExists(oldJar);
			} catch (Exception stillLocked) {
				// Minecraft is still closing
			}
			if (!Files.exists(oldJar)) {
				Files.move(newFile, finalJar, StandardCopyOption.REPLACE_EXISTING);
				break;
			}
		}
		// best effort: remove the copy of this class (it may stay locked while this process runs)
		try {
			Path cls = tempDir.resolve("net").resolve("parz").resolve("update").resolve("SwapHelper.class");
			Files.deleteIfExists(cls);
			Files.deleteIfExists(tempDir.resolve("net").resolve("parz").resolve("update"));
			Files.deleteIfExists(tempDir.resolve("net").resolve("parz"));
			Files.deleteIfExists(tempDir.resolve("net"));
			Files.deleteIfExists(tempDir);
		} catch (Exception ignored) {
			// the temp folder stays, harmless
		}
	}
}
