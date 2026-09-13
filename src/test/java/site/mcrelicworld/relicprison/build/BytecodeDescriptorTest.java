package site.mcrelicworld.relicprison.build;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class BytecodeDescriptorTest {
    @Test
    void bytecodeDoesNotContainKnownIncorrectPaperDescriptors() throws IOException {
        List<String> failures = new ArrayList<>();
        try (var files = Files.walk(Path.of("target/classes"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassReader reader = new ClassReader(Files.readAllBytes(file));
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String methodName,
                                                                  String methodDescriptor, boolean isInterface) {
                                String className = reader.getClassName();
                                if (opcode == Opcodes.INVOKEINTERFACE
                                        && owner.equals("org/bukkit/plugin/RegisteredServiceProvider")) {
                                    failures.add(className + " treats RegisteredServiceProvider as an interface");
                                }
                                if (owner.equals("org/bukkit/block/Block") && methodName.equals("getDrops")
                                        && methodDescriptor.equals("(Lorg/bukkit/inventory/ItemStack;Lorg/bukkit/entity/Player;)Ljava/util/Collection;")) {
                                    failures.add(className + " calls Block#getDrops(ItemStack, Player)");
                                }
                                if (owner.equals("org/bukkit/event/entity/EntityDamageEvent") && methodName.equals("getEntity")
                                        && methodDescriptor.equals("()Ljava/lang/Object;")) {
                                    failures.add(className + " sees EntityDamageEvent#getEntity as Object");
                                }
                                if (owner.equals("org/bukkit/event/inventory/InventoryClickEvent")
                                        && methodName.equals("getWhoClicked")
                                        && methodDescriptor.equals("()Ljava/lang/Object;")) {
                                    failures.add(className + " sees InventoryClickEvent#getWhoClicked as Object");
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(failures.isEmpty(), String.join(System.lineSeparator(), failures));
    }
}
