package semantic.pass;

import generated.CParser;
import generated.CParserBaseVisitor;
import semantic.ErrorReporter;
import semantic.SymbolTable;
import semantic.Types;

// 语句的语义检查：管理块级作用域和 for 循环（init 变量只属于循环）
// if/while/return 等只需默认递归的语句不在这里，由门面走默认 visitChildren
public class StatementPass {

    private final CParserBaseVisitor<String> walker;
    private final SymbolTable symbols;
    private final ErrorReporter reporter;

    public StatementPass(CParserBaseVisitor<String> walker, SymbolTable symbols, ErrorReporter reporter) {
        this.walker = walker;
        this.symbols = symbols;
        this.reporter = reporter;
    }

    public String compoundStmt(CParser.CompoundStmtContext ctx) {
        symbols.enterScope();
        walker.visitChildren(ctx);
        symbols.exitScope();
        return null;
    }

    public String forStmt(CParser.ForStmtContext ctx) {
        symbols.enterScope(); // for (int i = ...; ...) 的 i 只属于循环
        if (ctx.init != null) walker.visit(ctx.init);
        if (ctx.cond != null) {
            String condType = walker.visit(ctx.cond);
            if (!Types.isNumeric(condType)) {
                reporter.error(ctx.cond.getStart(), "for 条件必须是数值，实际是 " + condType);
            }
        }
        if (ctx.post != null) walker.visit(ctx.post);
        walker.visit(ctx.statement());
        symbols.exitScope();
        return null;
    }

    public String forInit(CParser.ForInitContext ctx) {
        if (ctx.declaration() != null) walker.visit(ctx.declaration());
        else if (ctx.expr() != null) walker.visit(ctx.expr());
        return null;
    }
}
