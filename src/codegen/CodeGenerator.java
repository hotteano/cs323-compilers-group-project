package codegen;

import codegen.pass.DeclarationPass;
import codegen.pass.ExpressionPass;
import codegen.pass.FunctionPass;
import codegen.pass.StatementPass;
import generated.CParser;
import generated.CParserBaseVisitor;

// 代码生成门面：ANTLR 遍历的唯一入口。
// 共享状态（IREmitter、VariableScopes、FunctionTable）在这里创建并注入各 pass，
// 所有 visitXxx 只做委托，具体翻译模板见 codegen.pass 包。
public class CodeGenerator extends CParserBaseVisitor<Value> {

    private final IREmitter ir = new IREmitter();
    private final VariableScopes vars = new VariableScopes();
    private final FunctionTable functions = new FunctionTable();

    private final FunctionPass functionPass;
    private final DeclarationPass declarations;
    private final StatementPass statements;
    private final ExpressionPass expressions;

    public CodeGenerator() {
        this.functionPass = new FunctionPass(this, ir, vars, functions);
        this.declarations = new DeclarationPass(this, ir, vars);
        this.statements = new StatementPass(this, ir, vars, functions);
        this.expressions = new ExpressionPass(this, ir, vars, functions);
    }

    public String getIR() {
        return ir.getIR();
    }

    // ── 顶层结构 ──

    @Override
    public Value visitTranslationUnit(CParser.TranslationUnitContext ctx) {
        return functionPass.translationUnit(ctx);
    }

    @Override
    public Value visitFunctionDef(CParser.FunctionDefContext ctx) {
        return functionPass.functionDef(ctx);
    }

    @Override
    public Value visitDeclaration(CParser.DeclarationContext ctx) {
        return declarations.declaration(ctx);
    }

    // ── 语句 ──

    @Override
    public Value visitCompoundStmt(CParser.CompoundStmtContext ctx) {
        return statements.compoundStmt(ctx);
    }

    @Override
    public Value visitReturnStmt(CParser.ReturnStmtContext ctx) {
        return statements.returnStmt(ctx);
    }

    @Override
    public Value visitBreakStmt(CParser.BreakStmtContext ctx) {
        return statements.breakStmt(ctx);
    }

    @Override
    public Value visitIfStmt(CParser.IfStmtContext ctx) {
        return statements.ifStmt(ctx);
    }

    @Override
    public Value visitWhileStmt(CParser.WhileStmtContext ctx) {
        return statements.whileStmt(ctx);
    }

    @Override
    public Value visitForStmt(CParser.ForStmtContext ctx) {
        return statements.forStmt(ctx);
    }

    @Override
    public Value visitForInit(CParser.ForInitContext ctx) {
        return statements.forInit(ctx);
    }

    @Override
    public Value visitExprStmt(CParser.ExprStmtContext ctx) {
        return statements.exprStmt(ctx);
    }

    // ── 表达式（全部委托给 ExpressionPass）──

    @Override
    public Value visitIntExpr(CParser.IntExprContext ctx)       { return expressions.intExpr(ctx); }
    @Override
    public Value visitFloatExpr(CParser.FloatExprContext ctx)   { return expressions.floatExpr(ctx); }
    @Override
    public Value visitCharExpr(CParser.CharExprContext ctx)     { return expressions.charExpr(ctx); }
    @Override
    public Value visitIdExpr(CParser.IdExprContext ctx)         { return expressions.idExpr(ctx); }
    @Override
    public Value visitParenExpr(CParser.ParenExprContext ctx)   { return expressions.parenExpr(ctx); }
    @Override
    public Value visitAddSub(CParser.AddSubContext ctx)         { return expressions.addSub(ctx); }
    @Override
    public Value visitMulDiv(CParser.MulDivContext ctx)         { return expressions.mulDiv(ctx); }
    @Override
    public Value visitCompare(CParser.CompareContext ctx)       { return expressions.compare(ctx); }
    @Override
    public Value visitEquality(CParser.EqualityContext ctx)     { return expressions.equality(ctx); }
    @Override
    public Value visitAssignExpr(CParser.AssignExprContext ctx) { return expressions.assignExpr(ctx); }
    @Override
    public Value visitArrayAccess(CParser.ArrayAccessContext ctx) { return expressions.arrayAccess(ctx); }
    @Override
    public Value visitArrayAssign(CParser.ArrayAssignContext ctx) { return expressions.arrayAssign(ctx); }
    @Override
    public Value visitPostInc(CParser.PostIncContext ctx)       { return expressions.postInc(ctx); }
    @Override
    public Value visitCallExpr(CParser.CallExprContext ctx)     { return expressions.callExpr(ctx); }
}
