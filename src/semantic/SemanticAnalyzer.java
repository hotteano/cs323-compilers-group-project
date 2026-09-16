package semantic;

import generated.CParser;
import generated.CParserBaseVisitor;
import java.util.List;
import semantic.pass.DeclarationPass;
import semantic.pass.ExpressionPass;
import semantic.pass.StatementPass;

// 语义分析门面：ANTLR 遍历的唯一入口。
// 共享状态（符号表、错误收集器）在这里创建并注入各 pass，
// 所有 visitXxx 只做委托，具体逻辑见 semantic.pass 包。
// visit 返回值约定为表达式节点的类型名（"int"/"float"/"char"/"string"/"int[]"）。
public class SemanticAnalyzer extends CParserBaseVisitor<String> {

    private final SymbolTable symbols = new SymbolTable();
    private final ErrorReporter reporter = new ErrorReporter();

    private final ExpressionPass expressions;
    private final DeclarationPass declarations;
    private final StatementPass statements;

    public SemanticAnalyzer() {
        this.expressions = new ExpressionPass(this, symbols, reporter);
        this.declarations = new DeclarationPass(this, symbols, reporter);
        this.statements = new StatementPass(this, symbols, reporter);
    }

    public List<String> getErrors() {
        return reporter.getErrors();
    }

    // ── 结构节点 ──

    @Override
    public String visitFunctionDef(CParser.FunctionDefContext ctx) {
        return declarations.functionDef(ctx);
    }

    @Override
    public String visitCompoundStmt(CParser.CompoundStmtContext ctx) {
        return statements.compoundStmt(ctx);
    }

    @Override
    public String visitDeclaration(CParser.DeclarationContext ctx) {
        return declarations.declaration(ctx);
    }

    @Override
    public String visitForStmt(CParser.ForStmtContext ctx) {
        return statements.forStmt(ctx);
    }

    @Override
    public String visitForInit(CParser.ForInitContext ctx) {
        return statements.forInit(ctx);
    }

    // ── 表达式节点（全部委托给 ExpressionPass）──

    @Override
    public String visitIntExpr(CParser.IntExprContext ctx)       { return expressions.intExpr(ctx); }
    @Override
    public String visitFloatExpr(CParser.FloatExprContext ctx)   { return expressions.floatExpr(ctx); }
    @Override
    public String visitCharExpr(CParser.CharExprContext ctx)     { return expressions.charExpr(ctx); }
    @Override
    public String visitStringExpr(CParser.StringExprContext ctx) { return expressions.stringExpr(ctx); }
    @Override
    public String visitIdExpr(CParser.IdExprContext ctx)         { return expressions.idExpr(ctx); }
    @Override
    public String visitParenExpr(CParser.ParenExprContext ctx)   { return expressions.parenExpr(ctx); }
    @Override
    public String visitMulDiv(CParser.MulDivContext ctx)         { return expressions.mulDiv(ctx); }
    @Override
    public String visitAddSub(CParser.AddSubContext ctx)         { return expressions.addSub(ctx); }
    @Override
    public String visitCompare(CParser.CompareContext ctx)       { return expressions.compare(ctx); }
    @Override
    public String visitEquality(CParser.EqualityContext ctx)     { return expressions.equality(ctx); }
    @Override
    public String visitPostInc(CParser.PostIncContext ctx)       { return expressions.postInc(ctx); }
    @Override
    public String visitAssignExpr(CParser.AssignExprContext ctx) { return expressions.assignExpr(ctx); }
    @Override
    public String visitCallExpr(CParser.CallExprContext ctx)     { return expressions.callExpr(ctx); }
    @Override
    public String visitArrayAccess(CParser.ArrayAccessContext ctx) { return expressions.arrayAccess(ctx); }
    @Override
    public String visitArrayAssign(CParser.ArrayAssignContext ctx) { return expressions.arrayAssign(ctx); }
}
