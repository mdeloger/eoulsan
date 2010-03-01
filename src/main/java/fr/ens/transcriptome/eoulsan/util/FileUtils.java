/*
 *                      Nividic development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the microarray platform
 * of the École Normale Supérieure and the individual authors.
 * These should be listed in @author doc comments.
 *
 * For more information on the Nividic project and its aims,
 * or to join the Nividic mailing list, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/nividic
 *
 */

package fr.ens.transcriptome.eoulsan.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileUtils {

  /** The default size of the buffer. */
  private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
  /** The charset to use. */
  private static final String CHARSET = "ISO-8859-1";

  /**
   * Simple FilenameFilter to filter Paths with their prefix.
   * @author Laurent Jourdren
   */
  public static final class PrefixFilenameFilter implements FilenameFilter {

    private String prefix;
    private boolean allowCompressedFile;

    @Override
    public boolean accept(final File file, final String name) {

      if (name == null)
        return false;

      final String myName;

      if (this.allowCompressedFile)
        myName = StringUtils.removeCompressedExtensionFromFilename(name);
      else
        myName = name;

      return myName.startsWith(this.prefix);
    }

    //
    // Constructor
    //

    /**
     * Public constructor.
     * @param prefix the prefix for the filter
     */
    public PrefixFilenameFilter(final String prefix) {

      this(prefix, false);
    }

    /**
     * Public constructor.
     * @param prefix the prefix for the filter
     * @param allowCompressedFile allow files with a compressed extension
     */
    public PrefixFilenameFilter(final String prefix,
        final boolean allowCompressedFile) {

      if (prefix == null)
        throw new NullPointerException("The prefix is null");

      this.prefix = prefix;
      this.allowCompressedFile = allowCompressedFile;
    }
  };

  /**
   * Simple FilenameFilter to filter Paths with their suffix.
   * @author Laurent Jourdren
   */
  public static final class SuffixFilenameFilter implements FilenameFilter {

    private String suffix;
    private boolean allowCompressedFile;

    @Override
    public boolean accept(final File file, final String name) {

      if (name == null)
        return false;

      final String myName;

      if (this.allowCompressedFile)
        myName = StringUtils.removeCompressedExtensionFromFilename(name);
      else
        myName = name;

      return myName.endsWith(this.suffix);
    }

    //
    // Constructor
    //

    /**
     * Public constructor.
     * @param suffix the suffix for the filter
     */
    public SuffixFilenameFilter(final String suffix) {

      this(suffix, false);
    }

    /**
     * Public constructor.
     * @param suffix the suffix for the filter
     * @param allowCompressedFile allow files with a compressed extension
     */
    public SuffixFilenameFilter(final String suffix,
        final boolean allowCompressedFile) {

      if (suffix == null)
        throw new NullPointerException("The suffix is null");

      this.suffix = suffix;
      this.allowCompressedFile = allowCompressedFile;
    }
  };

  /**
   * Utility method to create fast BufferedReader.
   * @param file File to read
   * @return a BufferedReader
   * @throws FileNotFoundException if the file is not found
   */
  public static final BufferedReader createBufferedReader(final File file)
      throws FileNotFoundException {

    if (file == null)
      return null;

    final FileInputStream inFile = new FileInputStream(file);
    final FileChannel inChannel = inFile.getChannel();

    return new BufferedReader(new InputStreamReader(Channels
        .newInputStream(inChannel)));
  }

  /**
   * Utility method to create fast BufferedWriter. Warning the buffer is not
   * safe-thread. The created file use ISO-8859-1 encoding.
   * @param file File to write
   * @return a BufferedWriter
   * @throws FileNotFoundException if the file is not found
   */
  public static final UnSynchronizedBufferedWriter createBufferedWriter(
      final File file) throws FileNotFoundException {

    if (file == null)
      return null;

    if (file.isFile())
      file.delete();

    final FileOutputStream outFile = new FileOutputStream(file);
    final FileChannel outChannel = outFile.getChannel();

    return new UnSynchronizedBufferedWriter(new OutputStreamWriter(Channels
        .newOutputStream(outChannel), Charset.forName(CHARSET)));
  }

  /**
   * Utility method to create fast BufferedWriter. Warning the buffer is not
   * safe-thread. The created file use ISO-8859-1 encoding.
   * @param file File to write
   * @return a BufferedWriter
   * @throws IOException if an error occurs while creating the Writer
   */
  public static final UnSynchronizedBufferedWriter createBufferedGZipWriter(
      final File file) throws IOException {

    if (file == null)
      return null;

    // Remove file if exists
    if (file.exists())
      file.delete();

    final FileOutputStream outFile = new FileOutputStream(file);
    final FileChannel outChannel = outFile.getChannel();

    final GZIPOutputStream gzos =
        new GZIPOutputStream(Channels.newOutputStream(outChannel));

    return new UnSynchronizedBufferedWriter(new OutputStreamWriter(gzos,
        Charset.forName(CHARSET)));
  }

  /**
   * Utility method to create fast ObjectOutput.
   * @param file File to write
   * @return a ObjectOutput
   * @throws IOException if an error occurs while creating the Writer
   */
  public static final ObjectOutputStream createObjectOutputWriter(
      final File file) throws IOException {

    if (file == null)
      return null;

    // Remove file if exists
    if (file.exists())
      file.delete();

    final FileOutputStream outFile = new FileOutputStream(file);
    final FileChannel outChannel = outFile.getChannel();

    return new ObjectOutputStream(Channels.newOutputStream(outChannel));
  }

  /**
   * Utility method to create fast ObjectInputStream.
   * @param file File to read
   * @return a ObjectInputStream
   * @throws IOException if an error occurs while creating the reader
   */
  public static final ObjectInputStream createObjectInputReader(final File file)
      throws IOException {

    if (file == null)
      return null;

    final FileInputStream inFile = new FileInputStream(file);
    final FileChannel inChannel = inFile.getChannel();

    return new ObjectInputStream(Channels.newInputStream(inChannel));
  }

  /**
   * Copy bytes from an InputStream to an OutputStream.
   * @param input the InputStream to read from
   * @param output the OutputStream to write to
   * @return the number of bytes copied
   * @throws IOException In case of an I/O problem
   */
  public static int copy(InputStream input, OutputStream output)
      throws IOException {
    byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
    int count = 0;
    int n = 0;
    while (-1 != (n = input.read(buffer))) {
      output.write(buffer, 0, n);
      count += n;
    }
    return count;
  }

  /**
   * Copy a file.
   * @param srcFile File to copy
   * @param destFile Destination file
   * @param overwrite overwrite existing file
   * @throws IOException if an error occurs while copying file
   */
  public static boolean copyFile(final File srcFile, final File destFile)
      throws IOException {

    return copyFile(srcFile, destFile, false);
  }

  /**
   * Copy a file.
   * @param srcFile File to copy
   * @param destFile Destination file
   * @param overwrite overwrite existing file
   * @throws IOException if an error occurs while copying file
   */
  public static boolean copyFile(final File srcFile, final File destFile,
      final boolean overwrite) throws IOException {

    if (srcFile == null)
      throw new NullPointerException("Input file is null");

    if (destFile == null)
      throw new NullPointerException("output file is null");

    if (!srcFile.exists())
      throw new IOException("Source file doesn't exists: " + srcFile);

    if (srcFile.isDirectory())
      throw new IOException("Can't copy/move a directory: " + srcFile);

    final File myDestFile;

    if (destFile.isDirectory())
      myDestFile = new File(destFile, srcFile.getName());
    else
      myDestFile = destFile;

    if (destFile.exists()) {

      if (!overwrite)
        return false;

      myDestFile.delete();
    }

    final FileChannel inChannel = new FileInputStream(srcFile).getChannel();
    final FileChannel outChannel =
        new FileOutputStream(myDestFile).getChannel();

    try {
      inChannel.transferTo(0, inChannel.size(), outChannel);
    } catch (IOException e) {
      throw e;
    } finally {
      if (inChannel != null)
        inChannel.close();
      if (outChannel != null)
        outChannel.close();
    }

    return true;
  }

  /**
   * Copy a file.
   * @param in File to copy
   * @param out Destination file
   * @param overwrite overwrite existing file
   * @throws IOException if an error occurs while copying file
   */
  public static boolean moveFile(final File srcFile, final File destFile)
      throws IOException {

    return moveFile(srcFile, destFile, true);
  }

  /**
   * Copy a file.
   * @param in File to copy
   * @param out Destination file
   * @param overwrite overwrite existing file
   * @throws IOException if an error occurs while copying file
   */
  public static boolean moveFile(final File srcFile, final File destFile,
      final boolean overwrite) throws IOException {

    return copyFile(srcFile, destFile, overwrite) && srcFile.delete();
  }

  /**
   * Create a zip archive with the content of a directory.
   * @param directory directory to compress
   * @param zipFile output file
   * @throws IOException if an error occurs while compressing data
   */
  public static void createZip(final File directory, final File zipFile)
      throws IOException {

    if (directory == null)
      throw new IOException("Input directory is null");

    if (!(directory.exists() && directory.isDirectory()))
      throw new IOException("Invalid directory (" + directory + ")");

    if (zipFile == null)
      throw new IOException("Output file is null");

    final ZipOutputStream out =
        new ZipOutputStream(new FileOutputStream(zipFile));

    BufferedInputStream origin = null;
    final File[] filesToAdd = directory.listFiles();

    final byte data[] = new byte[DEFAULT_BUFFER_SIZE];

    for (File f : filesToAdd) {

      out.putNextEntry(new ZipEntry(f.getName()));
      final FileInputStream fi = new FileInputStream(f);

      origin = new BufferedInputStream(fi, DEFAULT_BUFFER_SIZE);

      int count;
      while ((count = origin.read(data, 0, DEFAULT_BUFFER_SIZE)) != -1)
        out.write(data, 0, count);

      origin.close();
    }
    out.close();
  }

  /**
   * Unzip a zip file in a directory.
   * @param zipFile
   * @param outputDirectory
   * @throws IOException
   */
  public static void unzip(final File zipFile, final File outputDirectory)
      throws IOException {

    if (zipFile == null)
      throw new IOException("The zip file is null");

    if (!(zipFile.exists() && zipFile.isFile()))
      throw new IOException("Invalid zip file (" + zipFile.getName() + ")");

    if (outputDirectory == null)
      throw new IOException("The output directory is null");

    if (!(outputDirectory.exists() && outputDirectory.isDirectory()))
      throw new IOException("The output directory is invalid ("
          + outputDirectory + ")");

    BufferedOutputStream dest = null;
    final FileInputStream fis = new FileInputStream(zipFile);

    final ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
    ZipEntry entry;

    while ((entry = zis.getNextEntry()) != null) {

      int count;
      byte data[] = new byte[DEFAULT_BUFFER_SIZE];
      // write the files to the disk
      FileOutputStream fos =
          new FileOutputStream(new File(outputDirectory, entry.getName()));
      dest = new BufferedOutputStream(fos, DEFAULT_BUFFER_SIZE);

      while ((count = zis.read(data, 0, DEFAULT_BUFFER_SIZE)) != -1)
        dest.write(data, 0, count);

      dest.flush();
      dest.close();
    }
    zis.close();

  }

  /**
   * Get the files of a directory.
   * @param directory Directory to list files
   * @param extension extension of the file
   * @return an array of File objects
   */
  public static File[] listFilesByExtension(final File directory,
      final String extension) {

    if (directory == null || extension == null)
      return null;

    return directory.listFiles(new FilenameFilter() {

      public boolean accept(File arg0, String arg1) {

        return arg1.endsWith(extension);
      }
    });

  }

  /**
   * Remove a list of files.
   * @param filesToRemove An array with the files to remove
   * @param recursive true if the remove must be recursive
   */
  public static boolean removeFiles(final File[] filesToRemove,
      final boolean recursive) {

    if (filesToRemove == null)
      return false;

    for (int i = 0; i < filesToRemove.length; i++) {

      final File f = filesToRemove[i];

      if (f.isDirectory()) {
        if (recursive) {
          if (!removeFiles(listFilesByExtension(f, ""), true))
            return false;
          if (!f.delete())
            return false;
        }

      } else if (!f.delete())
        return false;
    }

    return true;
  }

  /**
   * Get the prefix of a list of files.
   * @param files Files that we wants the prefix
   * @return the prefix of the files
   */
  public static String getPrefix(final List<File> files) {

    if (files == null)
      return null;

    File[] param = new File[files.size()];
    files.toArray(param);

    return getPrefix(param);
  }

  /**
   * Get the prefix of a list of files.
   * @param files Files that we wants the prefix
   * @return the prefix of the files
   */
  public static String getPrefix(final File[] files) {

    if (files == null)
      return null;

    String prefix = null;
    final StringBuilder sb = new StringBuilder();

    for (int i = 0; i < files.length; i++) {

      String filename = files[i].getName();

      if (prefix == null)
        prefix = filename;
      else if (!filename.startsWith(prefix)) {

        int max = Math.min(prefix.length(), filename.length());

        for (int j = 0; j < max; j++) {

          if (prefix.charAt(j) == filename.charAt(j))
            sb.append(prefix.charAt(j));
        }

        prefix = sb.toString();
        sb.setLength(0);
      }

    }

    return prefix;
  }

  /**
   * Set executable bits on file on *nix.
   * @param file File to handle
   * @param executable If true, sets the access permission to allow execute
   *          operations; if false to disallow execute operations
   * @param ownerOnly If true, the execute permission applies only to the
   *          owner's execute permission; otherwise, it applies to everybody. If
   *          the underlying file system can not distinguish the owner's execute
   *          permission from that of others, then the permission will apply to
   *          everybody, regardless of this value.
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setExecutable(final File file,
      final boolean executable, final boolean ownerOnly) throws IOException {

    if (file == null)
      return false;

    if (!file.exists() || !file.isFile())
      throw new FileNotFoundException(file.getAbsolutePath());

    final String cmd =
        "chmod " + (ownerOnly ? "u+x " : "ugo+x ") + file.getAbsolutePath();

    ProcessUtils.exec(cmd, false);

    return true;
  }

  /**
   * Set executable bits on file on *nix.
   * @param file File to handle
   * @param ownerOnly If true, the execute permission applies only to the
   *          owner's execute permission; otherwise, it applies to everybody. If
   *          the underlying file system can not distinguish the owner's execute
   *          permission from that of others, then the permission will apply to
   *          everybody, regardless of this value.
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setExecutable(final File file, boolean executable)
      throws IOException {
    return setExecutable(file, executable, false);
  }

  /**
   * Set readable bits on file on *nix.
   * @param file File to handle
   * @param readable If true, sets the access permission to allow read
   *          operations; if false to disallow execute operations
   * @param ownerOnly If true, the execute permission applies only to the
   *          owner's execute permission; otherwise, it applies to everybody. If
   *          the underlying file system can not distinguish the owner's execute
   *          permission from that of others, then the permission will apply to
   *          everybody, regardless of this value.
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setReadable(final File file, final boolean readable,
      final boolean ownerOnly) throws IOException {

    if (file == null)
      return false;

    if (!file.exists() || !file.isFile())
      throw new FileNotFoundException(file.getAbsolutePath());

    final String cmd =
        "chmod " + (ownerOnly ? "u+r " : "ugo+r ") + file.getAbsolutePath();

    ProcessUtils.exec(cmd, true);

    return true;
  }

  /**
   * Set readable bits on file on *nix.
   * @param file File to handle
   * @param readable If true, sets the access permission to allow read
   *          operations; if false to disallow execute operations
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setReadable(final File file, boolean readable)
      throws IOException {
    return setReadable(file, readable, true);
  }

  /**
   * Set writable bits on file on *nix.
   * @param file File to handle
   * @param writable If true, sets the access permission to allow read
   *          operations; if false to disallow execute operations
   * @param ownerOnly If true, the execute permission applies only to the
   *          owner's execute permission; otherwise, it applies to everybody. If
   *          the underlying file system can not distinguish the owner's execute
   *          permission from that of others, then the permission will apply to
   *          everybody, regardless of this value.
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setWritable(final File file, final boolean writable,
      final boolean ownerOnly) throws IOException {

    if (file == null)
      return false;

    if (!file.exists() || !file.isFile())
      throw new FileNotFoundException(file.getAbsolutePath());

    final String cmd =
        "chmod " + (ownerOnly ? "u+w " : "ugo+w ") + file.getAbsolutePath();

    ProcessUtils.exec(cmd, true);

    return true;
  }

  /**
   * Set writable bits on file on *nix.
   * @param file File to handle
   * @param writable If true, sets the access permission to allow read
   *          operations; if false to disallow execute operations
   * @return true if and only if the operation succeeded
   * @throws IOException
   */
  public static boolean setWritable(final File file, boolean writable)
      throws IOException {
    return setWritable(file, writable, true);
  }

  /**
   * Delete a directory and its content. It is not a recurse method.
   * @param directory Directory to remove
   * @return false if one of the files can't be removed
   */
  public static boolean removeDirectory(final File directory) {

    if (directory == null)
      return false;

    final File[] files = directory.listFiles();
    for (int i = 0; i < files.length; i++)
      if (!files[i].delete())
        return false;

    return directory.delete();
  }

  /**
   * Concat a list of files
   * @param files files to concat
   * @param outputFile output file
   * @throws IOException if an error occurs while read or writing data
   */
  public static void concat(final List<File> files, final File outputFile)
      throws IOException {

    if (files == null)
      throw new NullPointerException("Files to concat is null");

    if (outputFile == null)
      throw new NullPointerException("Output file is null");

    UnSynchronizedBufferedWriter writer = createBufferedWriter(outputFile);

    for (File f : files) {

      BufferedReader reader = createBufferedReader(f);

      String line;

      while ((line = reader.readLine()) != null)
        writer.write(line + "\n");

    }

    writer.close();
  }

  /**
   * Create a new temporary file.
   * @param prefix Prefix of the temporary file
   * @param suffix suffix of the temporary file
   * @return the new temporary file
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempFile(final String prefix, final String suffix)
      throws IOException {

    return createTempFile(null, prefix, suffix);
  }

  /**
   * Create a new temporary file.
   * @param directory parent directory of the temporary file to create
   * @param prefix Prefix of the temporary file
   * @param suffix suffix of the temporary file
   * @return the new temporary file
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempFile(final File directory, final String prefix,
      final String suffix) throws IOException {

    final File myDir;
    final String myPrefix;
    final String mySuffix;

    if (directory == null)
      myDir = new File(System.getProperty("java.io.tmpdir"));
    else
      myDir = directory;

    if (prefix == null)
      myPrefix = "";
    else
      myPrefix = prefix;

    if (suffix == null)
      mySuffix = "";
    else
      mySuffix = suffix;

    File tempFile;

    final int maxAttempts = 9;
    int attemptCount = 0;
    do {
      attemptCount++;
      if (attemptCount > maxAttempts)

        throw new IOException("The highly improbable has occurred! Failed to "
            + "create a unique temporary directory after " + maxAttempts
            + " attempts.");

      final String filename =
          myPrefix + UUID.randomUUID().toString() + mySuffix;
      tempFile = new File(myDir, filename);
    } while (tempFile.exists());

    if (!tempFile.createNewFile())
      throw new IOException("Failed to create temp file named "
          + tempFile.getAbsolutePath());

    return tempFile;
  }

  /**
   * Create a new temporary directory.
   * @return the new directory
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempDir() throws IOException {

    return createTempDir(null, null);
  }

  /**
   * Create a new temporary directory.
   * @param prefix prefix of the temporary directory
   * @return the new directory
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempDir(final String prefix) throws IOException {

    return createTempDir(null, prefix);
  }

  /**
   * Create a new temporary directory.
   * @param parentDirectory parent directory for the temporary directory
   * @return the new directory
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempDir(final File parentDirectory)
      throws IOException {

    return createTempDir(parentDirectory, null);
  }

  /**
   * Create a new temporary directory.
   * @param parentDirectory parent directory for the temporary directory
   * @param prefix Prefix of the directory name
   * @return the new directory
   * @throws IOException if there is an error creating the temporary directory
   */
  public static File createTempDir(final File parentDirectory,
      final String prefix) throws IOException {

    final File myTempParentDir;
    final String myPrefix;

    if (parentDirectory == null)
      myTempParentDir = new File(System.getProperty("java.io.tmpdir"));
    else
      myTempParentDir = parentDirectory;

    if (prefix == null)
      myPrefix = "";
    else
      myPrefix = prefix;

    File newTempDir;
    final int maxAttempts = 9;
    int attemptCount = 0;
    do {
      attemptCount++;
      if (attemptCount > maxAttempts)

        throw new IOException("The highly improbable has occurred! Failed to "
            + "create a unique temporary directory after " + maxAttempts
            + " attempts.");

      String dirName = myPrefix + UUID.randomUUID().toString();
      newTempDir = new File(myTempParentDir, dirName);
    } while (newTempDir.exists());

    if (newTempDir.mkdirs())
      return newTempDir;

    throw new IOException("Failed to create temp dir named "
        + newTempDir.getAbsolutePath());

  }

  /**
   * Recursively delete file or directory
   * @param fileOrDir the file or dir to delete
   * @return true if all files are successfully deleted
   */
  public static boolean recursiveDelete(final File fileOrDir) {

    if (fileOrDir == null)
      return false;

    if (fileOrDir.isDirectory())
      // recursively delete contents
      for (File innerFile : fileOrDir.listFiles())
        if (!recursiveDelete(innerFile))
          return false;

    return fileOrDir.delete();
  }

  /**
   * Check if a file exists
   * @param file File to test
   * @param msgFileType message for the description of the file
   * @throws IOException if the file doesn't exists
   */
  public static final void checkExistingFile(final File file,
      final String msgFileType) throws IOException {

    if (msgFileType == null)
      throw new NullPointerException("Message file type for check is null");

    if (file == null)
      throw new NullPointerException("The " + msgFileType + " is null");

    if (!file.exists())
      throw new IOException("The "
          + msgFileType + " does not exists: " + file.getAbsolutePath());
  }

  /**
   * Check if a directory exists
   * @param directory directory to test
   * @param msgFileType message for the description of the file
   * @throws IOException if the file doesn't exists
   */
  public static final void checkExistingDirectoryFile(final File directory,
      final String msgFileType) throws IOException {

    checkExistingFile(directory, msgFileType);
    if (!directory.isDirectory())
      throw new IOException("The "
          + msgFileType + " is not a directory: " + directory.getAbsolutePath());
  }

  /**
   * Check if a file exists
   * @param file File to test
   * @param msgFileType message for the description of the file
   * @throws IOException if the file doesn't exists
   */
  public static final void checkExistingStandardFile(final File file,
      final String msgFileType) throws IOException {

    checkExistingFile(file, msgFileType);
    if (!file.isFile())
      throw new IOException("The "
          + msgFileType + " is  not a standard file: " + file.getAbsolutePath());
  }

  /**
   * Check if a file exists
   * @param file File to test
   * @param msgFileType message for the description of the file
   * @throws IOException if the file doesn't exists
   */
  public static final void checkExistingStandardFileOrDirectory(
      final File file, final String msgFileType) throws IOException {

    checkExistingDirectoryFile(file, msgFileType);
    if (!file.isFile() && !file.isDirectory())
      throw new IOException("The "
          + msgFileType + " is  not a standard file or a directory: "
          + file.getAbsolutePath());
  }

}
