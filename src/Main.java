import codegen.CodeGenerator;
import generated.CLexer;
import generated.CParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import semantic.SemanticAnalyzer;

// 编译流水线驱动：词法 → 语法 → 语义 → 代码生成，任一阶段失败即停止
public class Main {
    public static void main(String[] args) throws Exception {
        CharStream charStream = CharStreams.fromFileName(args[0]);

        CLexer lexer = new CLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CParser parser = new CParser(tokens);

        ParseTree tree = parser.translationUnit();

        if (parser.getNumberOfSyntaxErrors() > 0) {
            System.out.println("语法分析失败，停止编译");
            return;
        }

        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.visit(tree);
        if (!analyzer.getErrors().isEmpty()) {
            for (String err : analyzer.getErrors()) {
                System.out.println(err);
            }
            System.out.println("语义分析失败，停止编译");
            return;
        }

        CodeGenerator generator = new CodeGenerator();
        generator.visit(tree);

        String llPath = args[0].replaceAll("\\.c$", ".ll");
        Files.writeString(Path.of(llPath), generator.getIR());
        System.out.println("已生成 " + llPath);
    }
}
