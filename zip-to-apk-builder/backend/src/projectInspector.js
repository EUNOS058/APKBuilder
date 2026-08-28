const fs = require('fs');
const path = require('path');
const unzipper = require('unzipper');

/**
 * Safely extracts a ZIP file preventing ZipSlip / Path Traversal attacks.
 * Detects the actual Android root directory.
 */
async function extractAndInspectProject(zipPath, targetDir) {
  if (!fs.existsSync(targetDir)) {
    fs.mkdirSync(targetDir, { recursive: true });
  }

  // Extract safely
  const directory = await unzipper.Open.file(zipPath);
  for (const file of directory.files) {
    // Path traversal safety check
    const safePath = path.normalize(file.path).replace(/^(\.\.[\/\\])+/, '');
    const absolutePath = path.join(targetDir, safePath);

    if (!absolutePath.startsWith(path.resolve(targetDir))) {
      throw new Error(`Security Exception: Path traversal attempt detected in entry: ${file.path}`);
    }

    if (file.type === 'Directory') {
      fs.mkdirSync(absolutePath, { recursive: true });
    } else {
      fs.mkdirSync(path.dirname(absolutePath), { recursive: true });
      await new Promise((resolve, reject) => {
        file.stream()
          .pipe(fs.createWriteStream(absolutePath))
          .on('finish', resolve)
          .on('error', reject);
      });
    }
  }

  // Find root directory containing build.gradle or settings.gradle
  const projectRoot = findAndroidRoot(targetDir);
  if (!projectRoot) {
    throw new Error('Invalid Android project: Missing build.gradle or settings.gradle file in root or subdirectories.');
  }

  // Check for gradlew
  const gradlewPath = path.join(projectRoot, 'gradlew');
  const hasGradlew = fs.existsSync(gradlewPath);
  if (hasGradlew) {
    try {
      fs.chmodSync(gradlewPath, '755');
    } catch (err) {
      console.warn('Could not chmod gradlew:', err.message);
    }
  }

  return {
    projectRoot,
    hasGradlew,
    hasSettingsGradle: fs.existsSync(path.join(projectRoot, 'settings.gradle')) || fs.existsSync(path.join(projectRoot, 'settings.gradle.kts')),
    hasBuildGradle: fs.existsSync(path.join(projectRoot, 'build.gradle')) || fs.existsSync(path.join(projectRoot, 'build.gradle.kts'))
  };
}

function findAndroidRoot(dir) {
  const isAndroidDir = (d) => {
    try {
      const files = fs.readdirSync(d);
      return files.includes('build.gradle') ||
             files.includes('build.gradle.kts') ||
             files.includes('settings.gradle') ||
             files.includes('settings.gradle.kts');
    } catch (e) {
      return false;
    }
  };

  const queue = [dir];
  while (queue.length > 0) {
    const current = queue.shift();
    if (isAndroidDir(current)) {
      return current;
    }
    try {
      const entries = fs.readdirSync(current, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isDirectory() && entry.name !== 'node_modules' && entry.name !== '.git' && entry.name !== 'build' && entry.name !== '.gradle') {
          queue.push(path.join(current, entry.name));
        }
      }
    } catch (e) {
      // Ignore unreadable directories
    }
  }

  return null;
}

module.exports = {
  extractAndInspectProject,
  findAndroidRoot
};
