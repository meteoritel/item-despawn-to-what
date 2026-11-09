import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** 用 JDK 语言模型读取源码的有效公开面，继承成员按使用方类型替换泛型。 */
public final class ExportInterface {
    private static final String API = "com.meteorite.itemdespawntowhat.client.ui.kit.api.";
    private static final String KIT = "com.meteorite.itemdespawntowhat.client.ui.kit.";

    /** 参数依次为 kit 源码、输出、write/check、依赖白名单与宿主命名空间。 */
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        Path output = Path.of(args[1]);
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
             var paths = Files.walk(source)) {
            var sources = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-Xdoclint:all,-missing", "--release", "21", "-classpath", System.getProperty("java.class.path")),
                    null, files.getJavaFileObjectsFromPaths(sources));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            var roots = new ArrayList<TypeElement>();
            for (Element element : task.analyze()) {
                if (element instanceof TypeElement type && type.getQualifiedName().toString().startsWith(API)
                        && type.getModifiers().contains(Modifier.PUBLIC)) roots.add(type);
            }
            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR).toList();
            if (!errors.isEmpty()) throw new IllegalStateException(errors.toString());
            var trees = Trees.instance(task);
            var allowed = Files.readAllLines(Path.of(args[3]), StandardCharsets.UTF_8);
            var violations = new TreeSet<String>();
            for (var unit : units) new References(trees, unit, allowed, args[4], violations).scan(unit, null);
            if (!violations.isEmpty()) throw new IllegalStateException(String.join("\n", violations));
            var lines = new TreeSet<String>();
            for (TypeElement type : roots) collect(type, task.getElements(), task.getTypes(), lines);
            String actual = String.join("\n", lines) + "\n";
            if (args[2].equals("write")) {
                Files.createDirectories(output.toAbsolutePath().getParent());
                Files.writeString(output, actual, StandardCharsets.UTF_8);
            } else if (!Files.readString(output, StandardCharsets.UTF_8).equals(actual)) {
                Path candidate = output.resolveSibling(output.getFileName() + ".actual");
                Files.writeString(candidate, actual, StandardCharsets.UTF_8);
                throw new IllegalStateException("kit Interface 与基线不同：" + candidate);
            }
            System.out.println("kit 有效公开面核对完成：" + output);
        }
    }

    private static final class References extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final CompilationUnitTree unit;
        private final List<String> allowed;
        private final String hostNamespace;
        private final TreeSet<String> violations;

        private References(Trees trees, CompilationUnitTree unit, List<String> allowed, String hostNamespace,
                           TreeSet<String> violations) {
            this.trees = trees; this.unit = unit; this.allowed = allowed;
            this.hostNamespace = hostNamespace; this.violations = violations;
        }
        private void checkType() {
            Element element = trees.getElement(getCurrentPath());
            if (!(element instanceof TypeElement type)) return;
            String name = type.getQualifiedName().toString();
            if (name.startsWith(KIT)) return;
            boolean accepted = allowed.stream().anyMatch(rule -> rule.endsWith(".*")
                    ? name.startsWith(rule.substring(0, rule.length() - 1))
                    : name.equals(rule) || name.startsWith(rule + "."));
            if (!accepted) report("白名单外类型：" + name);
        }
        private void report(String reason) {
            TreePath path = getCurrentPath();
            long position = trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
            while (position < 0 && path.getParentPath() != null) {
                path = path.getParentPath();
                position = trees.getSourcePositions().getStartPosition(unit, path.getLeaf());
            }
            violations.add(unit.getSourceFile().getName() + ":" + unit.getLineMap().getLineNumber(position) + ": " + reason);
        }
        @Override public Void visitIdentifier(IdentifierTree tree, Void unused) {
            checkType();
            return super.visitIdentifier(tree, unused);
        }
        @Override public Void visitMemberSelect(MemberSelectTree tree, Void unused) {
            checkType();
            return super.visitMemberSelect(tree, unused);
        }
        @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            Element element = trees.getElement(new TreePath(getCurrentPath(), tree.getMethodSelect()));
            if (element instanceof ExecutableElement method && method.getEnclosingElement() instanceof TypeElement type
                    && !tree.getArguments().isEmpty()) {
                String owner = type.getQualifiedName().toString();
                String name = method.getSimpleName().toString();
                String argument = constant(tree.getArguments().getFirst());
                if (argument != null && owner.equals("net.minecraft.network.chat.Component") && name.startsWith("translatable")
                        && (argument.startsWith(hostNamespace + ".") || argument.contains("." + hostNamespace + ".")))
                    report("宿主本地化 key：" + argument);
                if (argument != null && owner.equals("net.minecraft.resources.ResourceLocation")
                        && ((name.equals("fromNamespaceAndPath") && argument.equals(hostNamespace))
                        || ((name.equals("parse") || name.equals("tryParse")) && argument.startsWith(hostNamespace + ":"))))
                    report("宿主资源命名空间：" + argument);
            }
            return super.visitMethodInvocation(tree, unused);
        }
        private String constant(ExpressionTree expression) {
            if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) return value;
            if (expression instanceof ParenthesizedTree parentheses) return constant(parentheses.getExpression());
            if (expression instanceof BinaryTree binary && binary.getKind() == Tree.Kind.PLUS) {
                String left = constant(binary.getLeftOperand());
                String right = constant(binary.getRightOperand());
                return left == null || right == null ? null : left + right;
            }
            Element element = trees.getElement(TreePath.getPath(unit, expression));
            return element instanceof VariableElement variable && variable.getConstantValue() instanceof String value ? value : null;
        }
    }

    private static void collect(TypeElement type, Elements elements, Types types, TreeSet<String> lines) {
        String owner = type.getQualifiedName().toString();
        lines.add(owner + " | " + type.getKind() + " " + type.getModifiers() + " " + type.getTypeParameters()
                + " bounds " + type.getTypeParameters().stream().map(parameter -> parameter.getBounds().toString()).toList()
                + " extends " + type.getSuperclass() + " implements " + type.getInterfaces());
        for (var component : type.getRecordComponents()) {
            lines.add(owner + " | RECORD_COMPONENT " + component.getSimpleName() + " : " + component.asType());
        }
        for (Element member : elements.getAllMembers(type)) {
            if (!member.getModifiers().contains(Modifier.PUBLIC) && !member.getModifiers().contains(Modifier.PROTECTED)) continue;
            if (!(member.getEnclosingElement() instanceof TypeElement declaring)
                    || !declaring.getQualifiedName().toString().startsWith(API)) continue;
            if (member instanceof TypeElement nested) {
                lines.add(owner + " | TYPE " + nested.getSimpleName() + " @ " + declaring.getQualifiedName());
                if (member.getEnclosingElement().equals(type)) collect(nested, elements, types, lines);
                continue;
            }
            var memberType = types.asMemberOf((DeclaredType) type.asType(), member);
            String signature = memberType.toString();
            if (member instanceof ExecutableElement method && memberType instanceof ExecutableType executable) {
                signature += " throws " + executable.getThrownTypes() + " varargs=" + method.isVarArgs()
                        + " bounds=" + executable.getTypeVariables().stream().map(variable -> variable.getUpperBound().toString()).toList();
            } else if (member instanceof VariableElement variable && variable.getConstantValue() != null) {
                signature += " = " + variable.getConstantValue();
            }
            lines.add(owner + " | " + member.getKind() + " " + member.getModifiers() + " "
                    + member.getSimpleName() + " : " + signature + " @ " + declaring.getQualifiedName());
        }
    }
}
