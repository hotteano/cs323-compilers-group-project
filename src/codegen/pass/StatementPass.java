package codegen.pass;

import codegen.FunctionTable;
import codegen.IREmitter;
import codegen.LLVMTypes;
import codegen.Value;
import codegen.VariableScopes;
import generated.CParser;
import generated.CParserBaseVisitor;
import java.util.ArrayDeque;
import java.util.Deque;

// 语句的代码生成：控制流翻译成基本块 + 分支指令
// breakLabels 是循环出口标签栈，break 跳到栈顶
public class StatementPass {

    private final CParserBaseVisitor<Value> walker;
    private final IREmitter ir;
    private final VariableScopes vars;
    private final FunctionTable functions;
    private final Deque<String> breakLabels = new ArrayDeque<>();

    public StatementPass(CParserBaseVisitor<Value> walker, IREmitter ir,
                         VariableScopes vars, FunctionTable functions) {
        this.walker = walker;
        this.ir = ir;
        this.vars = vars;
        this.functions = functions;
    }

    public Value compoundStmt(CParser.CompoundStmtContext ctx) {
        vars.enterScope();
        for (CParser.StatementContext s : ctx.statement()) {
            if (ir.isTerminated()) break; // return 之后的死代码不再生成
            walker.visit(s);
        }
        vars.exitScope();
        return null;
    }

    public Value returnStmt(CParser.ReturnStmtContext ctx) {
        String retType = functions.currentReturnType();
        if (ctx.expr() == null || "void".equals(retType)) {
            ir.emit("ret void");
        } else {
            Value v = LLVMTypes.convert(ir, walker.visit(ctx.expr()), retType);
            ir.emit("ret " + retType + " " + v.ref);
        }
        ir.markTerminated();
        return null;
    }

    public Value breakStmt(CParser.BreakStmtContext ctx) {
        ir.emit("br label %" + breakLabels.peek());
        ir.markTerminated();
        return null;
    }

    public Value ifStmt(CParser.IfStmtContext ctx) {
        Value cond = LLVMTypes.toI1(ir, walker.visit(ctx.expr()));
        String thenL = ir.newLabel("if.then");
        String contL = ir.newLabel("if.cont");
        boolean hasElse = ctx.statement().size() > 1;
        String elseL = hasElse ? ir.newLabel("if.else") : contL;

        ir.emit("br i1 " + cond.ref + ", label %" + thenL + ", label %" + elseL);

        ir.emitLabel(thenL);
        walker.visit(ctx.statement(0));
        if (!ir.isTerminated()) ir.emit("br label %" + contL);

        if (hasElse) {
            ir.emitLabel(elseL);
            walker.visit(ctx.statement(1));
            if (!ir.isTerminated()) ir.emit("br label %" + contL);
        }
        ir.emitLabel(contL);
        return null;
    }

    public Value whileStmt(CParser.WhileStmtContext ctx) {
        String condL = ir.newLabel("while.cond");
        String bodyL = ir.newLabel("while.body");
        String endL = ir.newLabel("while.end");

        ir.emit("br label %" + condL);
        ir.emitLabel(condL);
        Value cond = LLVMTypes.toI1(ir, walker.visit(ctx.expr()));
        ir.emit("br i1 " + cond.ref + ", label %" + bodyL + ", label %" + endL);

        ir.emitLabel(bodyL);
        breakLabels.push(endL);
        walker.visit(ctx.statement());
        breakLabels.pop();
        if (!ir.isTerminated()) ir.emit("br label %" + condL);

        ir.emitLabel(endL);
        return null;
    }

    public Value forStmt(CParser.ForStmtContext ctx) {
        String condL = ir.newLabel("for.cond");
        String bodyL = ir.newLabel("for.body");
        String endL = ir.newLabel("for.end");

        vars.enterScope(); // init 里声明的变量只在循环内可见
        if (ctx.init != null) walker.visit(ctx.init);

        ir.emit("br label %" + condL);
        ir.emitLabel(condL);
        if (ctx.cond != null) {
            Value cond = LLVMTypes.toI1(ir, walker.visit(ctx.cond));
            ir.emit("br i1 " + cond.ref + ", label %" + bodyL + ", label %" + endL);
        } else {
            ir.emit("br label %" + bodyL); // 无条件 = 死循环
        }

        ir.emitLabel(bodyL);
        breakLabels.push(endL);
        walker.visit(ctx.statement());
        breakLabels.pop();
        if (!ir.isTerminated()) {
            if (ctx.post != null) walker.visit(ctx.post); // 步进表达式在循环体之后、回到条件之前
            ir.emit("br label %" + condL);
        }

        ir.emitLabel(endL);
        vars.exitScope();
        return null;
    }

    public Value forInit(CParser.ForInitContext ctx) {
        if (ctx.declaration() != null) walker.visit(ctx.declaration());
        else if (ctx.expr() != null) walker.visit(ctx.expr());
        return null;
    }

    public Value exprStmt(CParser.ExprStmtContext ctx) {
        if (ctx.expr() != null) walker.visit(ctx.expr());
        return null;
    }
}
