import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;

/** Local archive reader. Requires the already supplied apktool 3.0.3 jar and JDK 17.
 * Never loads Android classes or executes archive code. Keep generated smali outside Git.
 */
public final class InspectFrameworkClass {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected: archive.jar class-descriptor-substring output-directory");
        }
        Path output = Path.of(args[2]);
        Files.createDirectories(output);
        int found = 0;
        try (ZipFile zip = new ZipFile(args[0])) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                byte[] bytes;
                try (var in = zip.getInputStream(entry)) { bytes = in.readAllBytes(); }
                var dex = new DexBackedDexFile(bytes, 0);
                for (int i = 0; i < dex.classCount; i++) {
                    ClassDef cls = (ClassDef) dex.classSection.get(i);
                    if (!cls.getType().contains(args[1])) continue;
                    String name = cls.getType().substring(1, cls.getType().length() - 1).replace('/', '_');
                    try (var writer = new BaksmaliWriter(Files.newBufferedWriter(output.resolve(name + ".smali")))) {
                        new ClassDefinition(new BaksmaliOptions(), cls).writeTo(writer);
                    }
                    System.out.println(entry.getName() + " " + cls.getType());
                    found++;
                }
            }
        }
        if (found == 0) throw new IllegalStateException("No matching class");
    }
}
