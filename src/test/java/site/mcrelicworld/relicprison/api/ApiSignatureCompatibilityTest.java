package site.mcrelicworld.relicprison.api;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ApiSignatureCompatibilityTest {
    private static final Path API_CLASSES = Path.of("target/classes/site/mcrelicworld/relicprison/api");
    private static final Path SNAPSHOT = Path.of("src/test/resources/api-signatures-0.6.1-mines.txt");

    @Test
    void publicApiSignaturesMatchSnapshot() throws Exception {
        String actual = String.join(System.lineSeparator(), currentSignatures()) + System.lineSeparator();
        if (Boolean.getBoolean("relicprison.writeApiSignatureSnapshot")) {
            Files.writeString(SNAPSHOT, actual);
        }
        String expected = Files.readString(SNAPSHOT);
        assertEquals(normalize(expected), normalize(actual),
                "Public API signatures changed. Update the snapshot only for intentional API changes.");
    }

    private static List<String> currentSignatures() throws IOException {
        List<String> signatures = new ArrayList<>();
        try (var files = Files.walk(API_CLASSES)) {
            for (Path path : files.filter(path -> path.toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.class"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                try (InputStream input = Files.newInputStream(path)) {
                    ClassReader reader = new ClassReader(input);
                    ApiClassVisitor visitor = new ApiClassVisitor();
                    reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    signatures.addAll(visitor.signatures());
                }
            }
        }
        signatures.sort(Comparator.naturalOrder());
        return signatures;
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static final class ApiClassVisitor extends ClassVisitor {
        private final List<String> signatures = new ArrayList<>();
        private String className;
        private boolean publicClass;

        private ApiClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            publicClass = (access & Opcodes.ACC_PUBLIC) != 0;
            if (!publicClass) return;
            className = dotted(name);
            signatures.add("TYPE " + access(access) + " " + kind(access) + " " + className
                    + " extends " + dotted(superName)
                    + " implements " + dottedInterfaces(interfaces)
                    + generic(signature));
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (publicClass && exported(access)) {
                signatures.add("FIELD " + className + " " + access(access) + " " + name + " " + descriptor + generic(signature));
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (publicClass && exported(access) && !syntheticOrBridge(access) && !name.equals("<clinit>")) {
                signatures.add("METHOD " + className + " " + access(access) + " " + name + descriptor
                        + generic(signature) + throwsClause(exceptions));
            }
            return null;
        }

        private List<String> signatures() {
            return signatures;
        }
    }

    private static boolean exported(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    private static boolean syntheticOrBridge(int access) {
        return (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0;
    }

    private static String access(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) flags.add("public");
        if ((access & Opcodes.ACC_PROTECTED) != 0) flags.add("protected");
        if ((access & Opcodes.ACC_STATIC) != 0) flags.add("static");
        if ((access & Opcodes.ACC_FINAL) != 0) flags.add("final");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) flags.add("abstract");
        if ((access & Opcodes.ACC_ENUM) != 0) flags.add("enum");
        if ((access & Opcodes.ACC_RECORD) != 0) flags.add("record");
        return String.join(",", flags);
    }

    private static String kind(int access) {
        if ((access & Opcodes.ACC_ANNOTATION) != 0) return "annotation";
        if ((access & Opcodes.ACC_INTERFACE) != 0) return "interface";
        if ((access & Opcodes.ACC_ENUM) != 0) return "enum";
        if ((access & Opcodes.ACC_RECORD) != 0) return "record";
        return "class";
    }

    private static String dotted(String internalName) {
        return internalName == null ? "<none>" : internalName.replace('/', '.');
    }

    private static String dottedInterfaces(String[] interfaces) {
        if (interfaces == null || interfaces.length == 0) return "[]";
        return Arrays.stream(interfaces).map(ApiSignatureCompatibilityTest::dotted).sorted().toList().toString();
    }

    private static String generic(String signature) {
        return signature == null ? "" : " signature=" + signature;
    }

    private static String throwsClause(String[] exceptions) {
        if (exceptions == null || exceptions.length == 0) return "";
        return " throws=" + Arrays.stream(exceptions)
                .map(ApiSignatureCompatibilityTest::dotted)
                .sorted()
                .toList();
    }
}
