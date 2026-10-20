package uu.game.main.game;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import uu.game.main.game.soldat.SoldatRule;

/**
 * Enforces, structurally, that no simulation class can reach an unseeded random number generator.
 *
 * <h2>Why this is a bytecode scan</h2>
 * The replay test proves that the paths it happens to drive are reproducible. It cannot prove
 * anything about a path nobody remembered to convert - a map variant, a new bot behaviour, a second
 * kind of pickup - and that is precisely where a stray {@code Utils.getRandomNumber} would be
 * reintroduced and silently cost reproducibility again. So instead of testing behaviour, this test
 * reads the constant pool of every compiled class under {@code uu.game.main.game} and fails if any
 * of them so much as mentions a process-wide generator. A grep over the sources would miss a
 * static import or a wildcard; the constant pool records the resolved owner either way.
 *
 * <p>{@code GameRandom} is the single exemption: owning a generator is its entire job.
 */
public class SimulationRandomnessTest {

  private static final String SIMULATION_PACKAGE = "uu/game/main/game";

  private static final String RANDOM_SOURCE = "uu/game/main/game/common/GameRandom";

  /** Types that produce numbers nobody can reproduce, whatever they are used for. */
  private static final Set<String> FORBIDDEN_TYPES = new HashSet<>(Arrays.asList(
    "uu/game/main/helper/Utils",
    "java/util/Random",
    "java/util/concurrent/ThreadLocalRandom",
    "java/security/SecureRandom"));

  /** Classes that are fine in general but have one unseeded method. */
  private static final String MATH = "java/lang/Math";

  /**
   * Classes that must be covered whatever the build layout is. A scan that silently found nothing
   * would otherwise be a permanently green test.
   */
  private static final List<String> MUST_BE_SCANNED = Arrays.asList(
    "uu/game/main/game/soldat/SoldatRule.class",
    "uu/game/main/game/soldat/SoldatBoard.class",
    "uu/game/main/game/soldat/SoldatBot.class",
    "uu/game/main/game/soldat/SoldatMap.class",
    "uu/game/main/game/soldat/specialability/BonusItemService.class",
    "uu/game/main/game/bulanci/BulanciRule.class");

  @Test
  public void noSimulationClassTouchesAProcessWideRandomGenerator() throws Exception {
    File classesRoot = new File(SoldatRule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    File packageRoot = new File(classesRoot, SIMULATION_PACKAGE);
    assertTrue("compiled simulation classes not found under " + packageRoot, packageRoot.isDirectory());

    List<File> classFiles = new ArrayList<>();
    collectClassFiles(packageRoot, classFiles);

    Set<String> scanned = new LinkedHashSet<>();
    List<String> violations = new ArrayList<>();
    for (File classFile : classFiles) {
      String name = relativeName(classesRoot, classFile);
      scanned.add(name);
      if (name.equals(RANDOM_SOURCE + ".class")) {
        continue;
      }
      for (String reference : forbiddenReferences(classFile)) {
        violations.add(name + " -> " + reference);
      }
    }

    for (String required : MUST_BE_SCANNED) {
      assertTrue("the scan missed " + required + ", so it proves nothing", scanned.contains(required));
    }

    if (!violations.isEmpty()) {
      fail("simulation code must draw every random number from " + RANDOM_SOURCE
        + " so a match can be replayed from its seed, but found:\n  " + String.join("\n  ", violations));
    }
  }

  private static void collectClassFiles(File directory, List<File> out) {
    File[] entries = directory.listFiles();
    if (entries == null) {
      return;
    }
    Arrays.sort(entries);
    for (File entry : entries) {
      if (entry.isDirectory()) {
        collectClassFiles(entry, out);
      } else if (entry.getName().endsWith(".class")) {
        out.add(entry);
      }
    }
  }

  private static String relativeName(File root, File file) {
    return file.getAbsolutePath().substring(root.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
  }

  /**
   * @return every reference this class makes to an unseeded generator, as {@code owner.member}
   */
  private static List<String> forbiddenReferences(File classFile) throws IOException {
    ConstantPool pool = ConstantPool.read(Files.readAllBytes(classFile.toPath()));
    List<String> found = new ArrayList<>();

    // Mentioning the type at all - as a field, a local or a `new` - is already a violation.
    for (String type : pool.classNames()) {
      if (FORBIDDEN_TYPES.contains(type)) {
        found.add(type);
      }
    }
    // Math is legitimate; Math.random() is not.
    for (String[] member : pool.memberReferences()) {
      if (MATH.equals(member[0]) && "random".equals(member[1])) {
        found.add(MATH + ".random");
      }
    }
    return found;
  }

  /**
   * Just enough of the class file format to list the types and members a class refers to. Parsing
   * the constant pool by hand avoids pulling in a bytecode library for twenty lines of work.
   */
  private static final class ConstantPool {

    private static final int UTF8 = 1;
    private static final int INTEGER = 3;
    private static final int FLOAT = 4;
    private static final int LONG = 5;
    private static final int DOUBLE = 6;
    private static final int CLASS = 7;
    private static final int STRING = 8;
    private static final int FIELD_REF = 9;
    private static final int METHOD_REF = 10;
    private static final int INTERFACE_METHOD_REF = 11;
    private static final int NAME_AND_TYPE = 12;
    private static final int METHOD_HANDLE = 15;
    private static final int METHOD_TYPE = 16;
    private static final int DYNAMIC = 17;
    private static final int INVOKE_DYNAMIC = 18;
    private static final int MODULE = 19;
    private static final int PACKAGE = 20;

    private final int[] tags;
    private final String[] utf8;
    private final int[] first;
    private final int[] second;

    private ConstantPool(int size) {
      tags = new int[size];
      utf8 = new String[size];
      first = new int[size];
      second = new int[size];
    }

    static ConstantPool read(byte[] bytes) throws IOException {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      in.readInt();
      in.readUnsignedShort();
      in.readUnsignedShort();

      ConstantPool pool = new ConstantPool(in.readUnsignedShort());
      for (int i = 1; i < pool.tags.length; i++) {
        int tag = in.readUnsignedByte();
        pool.tags[i] = tag;
        switch (tag) {
          case UTF8:
            pool.utf8[i] = in.readUTF();
            break;
          case INTEGER:
          case FLOAT:
            in.readInt();
            break;
          case LONG:
          case DOUBLE:
            in.readLong();
            // Longs and doubles occupy two constant pool slots; the second one is unusable.
            i++;
            break;
          case CLASS:
          case STRING:
          case METHOD_TYPE:
          case MODULE:
          case PACKAGE:
            pool.first[i] = in.readUnsignedShort();
            break;
          case FIELD_REF:
          case METHOD_REF:
          case INTERFACE_METHOD_REF:
          case NAME_AND_TYPE:
          case DYNAMIC:
          case INVOKE_DYNAMIC:
            pool.first[i] = in.readUnsignedShort();
            pool.second[i] = in.readUnsignedShort();
            break;
          case METHOD_HANDLE:
            in.readUnsignedByte();
            pool.first[i] = in.readUnsignedShort();
            break;
          default:
            throw new IOException("unknown constant pool tag " + tag);
        }
      }
      return pool;
    }

    /** Internal names ({@code java/util/Random}) of every type this class refers to. */
    Set<String> classNames() {
      Set<String> names = new LinkedHashSet<>();
      for (int i = 1; i < tags.length; i++) {
        if (tags[i] == CLASS) {
          String name = utf8[first[i]];
          if (name != null) {
            names.add(name);
          }
        }
      }
      return names;
    }

    /** Every referenced field and method, as {@code {ownerInternalName, memberName}}. */
    List<String[]> memberReferences() {
      List<String[]> references = new ArrayList<>();
      for (int i = 1; i < tags.length; i++) {
        if (tags[i] != FIELD_REF && tags[i] != METHOD_REF && tags[i] != INTERFACE_METHOD_REF) {
          continue;
        }
        String owner = utf8[first[first[i]]];
        String member = utf8[first[second[i]]];
        if (owner != null && member != null) {
          references.add(new String[] {owner, member});
        }
      }
      return references;
    }
  }
}
